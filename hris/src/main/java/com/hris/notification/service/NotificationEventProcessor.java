package com.hris.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.notification.entity.Notification;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationType;
import com.hris.notification.mapper.NotificationMapper;
import com.hris.notification.repository.NotificationEventRepository;
import com.hris.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns persisted {@link NotificationEvent} outbox rows into {@link Notification} records.
 *
 * <p>Replaces the former RabbitMQ consumer: the outbox table itself is the queue, and this
 * processor is the single consumer. Creating the notification and stamping
 * {@code deliveredAt} happen in ONE transaction, so an event is either fully processed or
 * untouched — {@link NotificationOutboxWorker} retries untouched rows.
 *
 * <p>Runs in {@code REQUIRES_NEW} because it is invoked from after-commit hooks, where the
 * caller's transaction has already completed and must not be reused.
 *
 * <p>Idempotent via {@code eventId} deduplication; supports MDC correlation tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventProcessor {

    private final NotificationRepository notificationRepository;
    private final NotificationEventRepository notificationEventRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationStreamService notificationStreamService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(NotificationEvent event) {
        if (event.getCorrelationId() != null) {
            MDC.put("correlationId", event.getCorrelationId().toString());
        }
        try {
            log.debug("Processing notification event type={}, targetUserId={}",
                event.getEventType(), event.getTargetUserId());

            // Idempotency: if a notification already exists for this event, just settle the row.
            if (event.getId() != null && notificationRepository.existsByEventId(event.getId())) {
                log.info("Skipping duplicate notification event id={}, type={}",
                    event.getId(), event.getEventType());
                markDelivered(event);
                return;
            }

            User user = userRepository.findById(event.getTargetUserId())
                .orElse(null);

            if (user == null) {
                throw new IllegalStateException(
                    "Target user not found for notification event " + event.getEventType()
                );
            }

            // Resolve locale: prefer user preference, then event locale, then default to "fr".
            String preferred = user.getLocalePreference();
            if (preferred == null || preferred.isBlank()) {
                preferred = event.getLocale();
            }
            Locale locale = Locale.forLanguageTag(
                preferred != null && !preferred.isBlank() ? preferred : "fr");

            Object[] paramsArray = deserializeParams(event);

            String title;
            String body;
            try {
                title = messageSource.getMessage(event.getTitleKey(), paramsArray, locale);
                body = messageSource.getMessage(event.getBodyKey(), paramsArray, locale);
            } catch (Exception msgEx) {
                log.warn("MessageSource failed for notification keys [{}, {}], locale={}, exception={}: {}",
                    event.getTitleKey(), event.getBodyKey(), locale, msgEx.getClass().getName(), msgEx.getMessage());
                // Use raw keys as fallback so notification is still created
                title = event.getTitleKey();
                body = event.getBodyKey();
            }

            String linkPath = extractLinkPath(event);
            NotificationType notifType = resolveNotificationType(event.getEventType());
            String actorDisplayName = extractActorDisplayName(event.getEventType(), paramsArray);

            Notification notification = Notification.builder()
                .userId(user.getId())
                .title(title)
                .body(body)
                .linkPath(linkPath)
                .eventId(event.getId())
                .type(notifType)
                .actorDisplayName(actorDisplayName)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

            Notification saved = notificationRepository.save(notification);
            markDelivered(event);
            pushAfterCommit(saved);

            log.info("Notification saved for userId={} from event type={}", user.getId(), event.getEventType());

        } catch (Exception e) {
            log.error("Failed to process notification event: {}", event.getEventType(), e);
            throw new IllegalStateException("Failed to process notification event " + event.getEventType(), e);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void markDelivered(NotificationEvent event) {
        event.setDeliveredAt(Instant.now());
        notificationEventRepository.save(event);
    }

    /**
     * Real-time SSE push, deferred until the notification row is committed so subscribers
     * never see a notification that subsequently rolls back.
     */
    private void pushAfterCommit(Notification notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationStreamService.push(notification.getUserId(), notificationMapper.toDto(notification));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationStreamService.push(notification.getUserId(), notificationMapper.toDto(notification));
            }
        });
    }

    /**
     * Converts JSON params to Object[].
     * Supports both Map and List formats.
     */
    private Object[] deserializeParams(NotificationEvent event) {
        String paramsJson = event.getParams();
        if (paramsJson == null || paramsJson.isBlank()) {
            return new Object[0];
        }
        try {
            Map<String, Object> map = objectMapper.readValue(paramsJson,
                new TypeReference<Map<String, Object>>() {});
            return mapParamsForEvent(event.getEventType(), map);
        } catch (Exception e) {
            try {
                List<Object> list = objectMapper.readValue(paramsJson,
                    new TypeReference<List<Object>>() {});
                return list.toArray();
            } catch (Exception ex) {
                log.warn("Failed to deserialize notification params for event type={}", event.getEventType());
                return new Object[]{paramsJson};
            }
        }
    }

    private Object[] mapParamsForEvent(com.hris.notification.enums.NotificationEventType eventType,
                                        Map<String, Object> map) {
        return switch (eventType) {
            case LEAVE_SUBMITTED, LEAVE_APPROVED, LEAVE_REJECTED -> new Object[]{
                map.getOrDefault("employeeName", ""),
                map.getOrDefault("startDate", ""),
                map.getOrDefault("endDate", ""),
                map.getOrDefault("workingDays", "")
            };
            case LEAVE_CANCELLED -> new Object[]{
                map.getOrDefault("employeeName", ""),
                map.getOrDefault("startDate", ""),
                map.getOrDefault("endDate", ""),
                map.getOrDefault("workingDays", "")
            };
            case LEAVE_BALANCE_ADJUSTED -> new Object[]{
                map.getOrDefault("leaveTypeName", ""),
                map.getOrDefault("adjustmentAmount", ""),
                map.getOrDefault("newBalance", "")
            };
            case LEAVE_ACCRUAL_APPLIED -> new Object[]{
                map.getOrDefault("policiesProcessed", ""),
                map.getOrDefault("transactionsCreated", ""),
                map.getOrDefault("runDate", "")
            };
            case ADMIN_REQUEST_SUBMITTED -> new Object[]{
                map.getOrDefault("requestNumber", ""),
                map.getOrDefault("requestType", "")
            };
            case ADMIN_REQUEST_CREATED,
                 ADMIN_REQUEST_IN_REVIEW,
                 ADMIN_REQUEST_APPROVED,
                 ADMIN_REQUEST_COMPLETED,
                 ADMIN_REQUEST_CANCELLED,
                 ADMIN_REQUEST_COMMENT_ADDED,
                 ADMIN_REQUEST_ATTACHMENT_ADDED,
                 ADMIN_REQUEST_RESPONSE_ATTACHMENT_ADDED -> new Object[]{
                map.getOrDefault("requestNumber", ""),
                map.getOrDefault("subject", "")
            };
            case ADMIN_REQUEST_REJECTED -> new Object[] {
                map.getOrDefault("requestNumber", ""),
                map.getOrDefault("rejectionReason", "")
            };
            case ADMIN_REQUEST_SLA_EXCEEDED -> new Object[] {
                map.getOrDefault("requestNumber", ""),
                map.getOrDefault("subject", ""),
                map.getOrDefault("dueAt", "")
            };
            case PROJECT_ASSIGNED -> new Object[] {
                map.getOrDefault("projectName", "")
            };
            case TIMESHEET_SUBMITTED, TIMESHEET_APPROVED, TIMESHEET_REJECTED -> new Object[] {
                map.getOrDefault("employeeName", ""),
                map.getOrDefault("periodStart", ""),
                map.getOrDefault("periodEnd", ""),
                map.getOrDefault("rejectionReason", "")
            };
            case EMPLOYEE_TERMINATED, CONTRACT_EXPIRING, CONTRACT_EXPIRED, PROBATION_ENDING -> new Object[] {
                map.getOrDefault("employeeName", ""),
                map.getOrDefault("date", ""),
                map.getOrDefault("contractType", "")
            };
            case EMPLOYEE_TRANSFERRED -> new Object[] {
                map.getOrDefault("employeeName", ""),
                map.getOrDefault("date", ""),
                map.getOrDefault("departmentName", "")
            };
            case DOCUMENT_EXPIRING, DOCUMENT_EXPIRED -> new Object[] {
                map.getOrDefault("employeeName", ""),
                map.getOrDefault("title", ""),
                map.getOrDefault("date", "")
            };
            case PERFORMANCE_CYCLE_OPENED, PERFORMANCE_SELF_ASSESSMENT_DUE -> new Object[] {
                map.getOrDefault("cycleName", ""),
                map.getOrDefault("date", "")
            };
            case PERFORMANCE_REVIEW_SUBMITTED, PERFORMANCE_REVIEW_READY_FOR_ACK,
                 PERFORMANCE_REVIEW_COMPLETED,
                 PERFORMANCE_FEEDBACK_REQUESTED, PERFORMANCE_FEEDBACK_SUBMITTED -> new Object[] {
                map.getOrDefault("employeeName", ""),
                map.getOrDefault("cycleName", "")
            };
        };
    }

    private String extractLinkPath(NotificationEvent event) {
        String paramsJson = event.getParams();
        if (paramsJson == null || paramsJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(paramsJson,
                new TypeReference<Map<String, Object>>() {});
            Object value = map.containsKey("linkPath") ? map.get("linkPath") : map.get("targetPath");
            if (value instanceof String path && !path.isBlank()) {
                return path;
            }
        } catch (Exception ex) {
            log.debug("Notification event does not expose a link path for {}", event.getEventType(), ex);
        }
        return null;
    }

    private NotificationType resolveNotificationType(com.hris.notification.enums.NotificationEventType eventType) {
        return switch (eventType) {
            case LEAVE_SUBMITTED, LEAVE_APPROVED, LEAVE_REJECTED, LEAVE_CANCELLED,
                 LEAVE_BALANCE_ADJUSTED, LEAVE_ACCRUAL_APPLIED -> NotificationType.LEAVE;
            case ADMIN_REQUEST_CREATED, ADMIN_REQUEST_SUBMITTED, ADMIN_REQUEST_IN_REVIEW,
                 ADMIN_REQUEST_APPROVED, ADMIN_REQUEST_REJECTED, ADMIN_REQUEST_COMPLETED,
                 ADMIN_REQUEST_CANCELLED, ADMIN_REQUEST_COMMENT_ADDED,
                 ADMIN_REQUEST_ATTACHMENT_ADDED, ADMIN_REQUEST_RESPONSE_ATTACHMENT_ADDED,
                 ADMIN_REQUEST_SLA_EXCEEDED -> NotificationType.REQUEST;
            case PROJECT_ASSIGNED -> NotificationType.TEAM;
            case TIMESHEET_SUBMITTED -> NotificationType.APPROVAL;
            case TIMESHEET_APPROVED, TIMESHEET_REJECTED -> NotificationType.REQUEST;
            case EMPLOYEE_TERMINATED, EMPLOYEE_TRANSFERRED -> NotificationType.TEAM;
            case CONTRACT_EXPIRING, CONTRACT_EXPIRED, PROBATION_ENDING,
                 DOCUMENT_EXPIRING, DOCUMENT_EXPIRED -> NotificationType.SYSTEM;
            case PERFORMANCE_CYCLE_OPENED, PERFORMANCE_SELF_ASSESSMENT_DUE,
                 PERFORMANCE_REVIEW_SUBMITTED, PERFORMANCE_REVIEW_READY_FOR_ACK,
                 PERFORMANCE_REVIEW_COMPLETED,
                 PERFORMANCE_FEEDBACK_REQUESTED, PERFORMANCE_FEEDBACK_SUBMITTED -> NotificationType.PERFORMANCE;
        };
    }

    private String extractActorDisplayName(com.hris.notification.enums.NotificationEventType eventType, Object[] params) {
        return switch (eventType) {
            case LEAVE_SUBMITTED, LEAVE_APPROVED, LEAVE_REJECTED, LEAVE_CANCELLED, TIMESHEET_SUBMITTED ->
                params.length > 0 ? String.valueOf(params[0]) : null;
            default -> null;
        };
    }
}
