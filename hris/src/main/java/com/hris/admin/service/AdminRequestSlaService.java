package com.hris.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.event.ActorType;
import com.hris.common.event.SystemActor;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects overdue admin requests and publishes SLA exceeded notifications.
 * Runs on a configurable schedule and uses slaNotifiedAt to prevent duplicate alerts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRequestSlaService {

    private final AdminRequestRepository adminRequestRepository;
    private final NotificationPublisher notificationPublisher;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Value("${app.admin.sla-check.enabled:false}")
    private boolean slaCheckEnabled;

    /**
     * Scheduled SLA check. Configurable via app.admin.sla-check.cron.
     * Defaults to every 4 hours.
     */
    @Scheduled(cron = "${app.admin.sla-check.cron:0 0 */4 * * *}")
    public void runScheduledSlaCheck() {
        if (!slaCheckEnabled) {
            return;
        }
        log.info("Starting scheduled SLA check for overdue admin requests");
        int notified = checkAndNotifySlaExceeded();
        log.info("SLA check completed: {} overdue requests notified", notified);
    }

    @Transactional
    public int checkAndNotifySlaExceeded() {
        Instant now = Instant.now();
        // Only re-notify if last notification was more than 24 hours ago
        Instant renotifyThreshold = now.minus(24, ChronoUnit.HOURS);

        List<AdminRequest> overdueRequests = adminRequestRepository.findOverdueRequests(
            now,
            List.of(AdminRequestStatus.SUBMITTED, AdminRequestStatus.IN_REVIEW),
            renotifyThreshold
        );

        if (overdueRequests.isEmpty()) {
            return 0;
        }

        // Find users with admin request processing permissions
        List<User> processors = userRepository.findByPermissionNames(
            List.of("ADMIN_REQUEST_INBOX_READ", "ADMIN_REQUEST_PROCESS")
        );

        int notifiedCount = 0;
        for (AdminRequest request : overdueRequests) {
            try {
                for (User processor : processors) {
                    publishSlaExceededNotification(processor, request);
                }
                request.setSlaNotifiedAt(now);
                adminRequestRepository.save(request);

                auditLogService.log(SystemActor.SYSTEM_ACTOR_ID, ActorType.SYSTEM,
                    AuditAction.SLA_EXCEEDED, "admin_request",
                    request.getId(), null, request);

                notifiedCount++;
            } catch (Exception e) {
                log.error("Failed to process SLA notification for request {}", request.getId(), e);
            }
        }

        return notifiedCount;
    }

    @Transactional(readOnly = true)
    public List<AdminRequest> findOverdueRequests() {
        return adminRequestRepository.findOverdueRequests(
            Instant.now(),
            List.of(AdminRequestStatus.SUBMITTED, AdminRequestStatus.IN_REVIEW),
            Instant.now().minus(24, ChronoUnit.HOURS)
        );
    }

    private void publishSlaExceededNotification(User processor, AdminRequest request) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("requestNumber", request.getRequestNumber());
            params.put("subject", request.getSubject());
            params.put("dueAt", request.getDueAt() != null ? request.getDueAt().toString() : "N/A");
            params.put("linkPath", "/admin/admin-requests");

            notificationPublisher.publish(NotificationEvent.builder()
                .eventType(NotificationEventType.ADMIN_REQUEST_SLA_EXCEEDED)
                .targetUserId(processor.getId())
                .titleKey("admin.sla.exceeded.title")
                .bodyKey("admin.sla.exceeded.body")
                .params(objectMapper.writeValueAsString(params))
                .locale(processor.getLocalePreference())
                .routingKey("system.admin.sla.exceeded")
                .publishedAt(Instant.now())
                .build());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize SLA exceeded notification params for request {}", request.getId(), e);
        }
    }
}
