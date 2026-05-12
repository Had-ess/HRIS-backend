package com.hris.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.config.RabbitMQConfig;
import com.hris.notification.entity.Notification;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Consumes notification events from RabbitMQ queues and persists Notification records.
 * Supports idempotency via eventId deduplication and MDC correlation tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_LEAVE)
    public void onLeaveEvent(org.springframework.amqp.core.Message message) {
        log.debug("Received leave notification message id={}, routingKey={}",
            message.getMessageProperties().getMessageId(),
            message.getMessageProperties().getReceivedRoutingKey());
        NotificationEvent event = deserializeMessage(message);
        processEvent(event);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ADMIN)
    public void onAdminEvent(org.springframework.amqp.core.Message message) {
        log.debug("Received admin notification message id={}, routingKey={}",
            message.getMessageProperties().getMessageId(),
            message.getMessageProperties().getReceivedRoutingKey());
        NotificationEvent event = deserializeMessage(message);
        processEvent(event);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SYSTEM)
    public void onSystemEvent(org.springframework.amqp.core.Message message) {
        log.debug("Received system notification message id={}, routingKey={}",
            message.getMessageProperties().getMessageId(),
            message.getMessageProperties().getReceivedRoutingKey());
        NotificationEvent event = deserializeMessage(message);
        processEvent(event);
    }

    private NotificationEvent deserializeMessage(org.springframework.amqp.core.Message message) {
        try {
            return objectMapper.readValue(message.getBody(), NotificationEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize notification message: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to deserialize notification event", e);
        }
    }

    private void processEvent(NotificationEvent event) {
        // Set MDC for correlation tracking
        if (event.getCorrelationId() != null) {
            MDC.put("correlationId", event.getCorrelationId().toString());
        }
        try {
            log.debug("Processing notification event type={}, targetUserId={}",
                event.getEventType(), event.getTargetUserId());

            // Idempotency check: skip if notification already exists for this event
            if (event.getId() != null && notificationRepository.existsByEventId(event.getId())) {
                log.info("Skipping duplicate notification event id={}, type={}",
                    event.getId(), event.getEventType());
                return;
            }

            User user = userRepository.findById(event.getTargetUserId())
                .orElse(null);

            if (user == null) {
                throw new IllegalStateException(
                    "Target user not found for notification event " + event.getEventType()
                );
            }

            log.debug("Found target user for notification event type={}, userId={}",
                event.getEventType(), user.getId());

            // Use user's locale preference, not event's locale
            Locale locale = Locale.forLanguageTag(
                user.getLocalePreference() != null ? user.getLocalePreference() : "fr");

            Object[] paramsArray = deserializeParams(event);
            log.debug("Deserialized {} notification params for event type={}",
                paramsArray.length, event.getEventType());

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
            log.debug("Resolved link path for event type={}: {}", event.getEventType(), linkPath);

            Notification notification = Notification.builder()
                .userId(user.getId())
                .title(title)
                .body(body)
                .linkPath(linkPath)
                .eventId(event.getId())
                .isRead(false)
                .createdAt(Instant.now())
                .build();

            notificationRepository.save(notification);
            log.info("Notification saved for userId={} from event type={}", user.getId(), event.getEventType());

        } catch (Exception e) {
            log.error("Failed to process notification event: {}", event.getEventType(), e);
            throw new IllegalStateException("Failed to process notification event " + event.getEventType(), e);
        } finally {
            MDC.remove("correlationId");
        }
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
}
