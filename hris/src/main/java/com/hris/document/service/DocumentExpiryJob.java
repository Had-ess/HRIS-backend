package com.hris.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.document.entity.EmployeeDocument;
import com.hris.document.repository.EmployeeDocumentRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.tenancy.TenantJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily document-expiry sweep (DOCUMENTS_DESIGN.md §7): warns the employee and
 * HR (DOCUMENT_MANAGE holders) once per document — DOCUMENT_EXPIRING when the
 * expiry date is within {@link #WARNING_DAYS}, DOCUMENT_EXPIRED when already
 * past. One expiry_notified_at stamp per document; a document warned ahead of
 * time is not re-notified at expiry (documented simplification, mirrors
 * contract alerts).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentExpiryJob {

    static final int WARNING_DAYS = 30;
    private static final List<String> HR_PERMISSIONS = List.of("DOCUMENT_MANAGE");

    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final TransactionalNotificationPublisher notificationPublisher;
    private final TenantJobRunner tenantJobRunner;
    private final ObjectMapper objectMapper;

    @Value("${app.documents.daily.enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "${app.documents.daily.cron:0 15 5 * * *}")
    @SchedulerLock(name = "documentExpiryJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT2M")
    public void runDailySweep() {
        if (!enabled) {
            return;
        }
        tenantJobRunner.forEachActiveTenant("documentExpiryJob", tenant -> {
            int alerts = sweepExpiringDocuments();
            log.info("Document expiry sweep for tenant {}: {} alerts", tenant.getSlug(), alerts);
        });
    }

    @Transactional
    public int sweepExpiringDocuments() {
        LocalDate today = LocalDate.now();
        List<EmployeeDocument> due = documentRepository
            .findByExpiryDateLessThanEqualAndExpiryNotifiedAtIsNull(today.plusDays(WARNING_DAYS));
        if (due.isEmpty()) {
            return 0;
        }

        List<User> hrUsers = userRepository.findByPermissionNames(HR_PERMISSIONS);
        int alerts = 0;
        for (EmployeeDocument document : due) {
            try {
                NotificationEventType eventType = document.getExpiryDate().isBefore(today)
                    ? NotificationEventType.DOCUMENT_EXPIRED
                    : NotificationEventType.DOCUMENT_EXPIRING;
                String titleKey = eventType == NotificationEventType.DOCUMENT_EXPIRED
                    ? "document.expired.title" : "document.expiring.title";
                String bodyKey = eventType == NotificationEventType.DOCUMENT_EXPIRED
                    ? "document.expired.body" : "document.expiring.body";

                Employee employee = employeeRepository.findById(document.getEmployeeId()).orElse(null);
                if (employee == null) {
                    continue;
                }
                String employeeName = displayName(employee);

                // the employee himself, linked to his own vault
                User owner = userRepository.findById(employee.getUserId()).orElse(null);
                if (owner != null) {
                    publish(eventType, owner, document, employeeName, titleKey, bodyKey, "/documents");
                    alerts++;
                }
                // HR, linked to the employee's detail page
                for (User hr : hrUsers) {
                    if (owner != null && hr.getId().equals(owner.getId())) {
                        continue;
                    }
                    publish(eventType, hr, document, employeeName, titleKey, bodyKey,
                        "/employees/" + employee.getId());
                    alerts++;
                }

                document.setExpiryNotifiedAt(Instant.now());
                documentRepository.save(document);
            } catch (Exception e) {
                log.error("Failed to process expiry alert for document {}", document.getId(), e);
            }
        }
        return alerts;
    }

    private void publish(NotificationEventType eventType, User target, EmployeeDocument document,
                         String employeeName, String titleKey, String bodyKey, String linkPath) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("employeeName", employeeName);
            params.put("title", document.getTitle());
            params.put("date", String.valueOf(document.getExpiryDate()));
            params.put("linkPath", linkPath);

            notificationPublisher.publishAfterCommit(NotificationEvent.builder()
                .eventType(eventType)
                .targetUserId(target.getId())
                .titleKey(titleKey)
                .bodyKey(bodyKey)
                .params(objectMapper.writeValueAsString(params))
                .locale(target.getLocalePreference())
                .routingKey("document." + eventType.name().toLowerCase())
                .publishedAt(Instant.now())
                .build());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize document expiry params for document {}", document.getId(), e);
        }
    }

    private String displayName(Employee employee) {
        UUID userId = employee.getUserId();
        if (userId == null) {
            return employee.getEmployeeCode();
        }
        return userRepository.findById(userId)
            .map(u -> ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim())
            .filter(name -> !name.isBlank())
            .orElse(employee.getEmployeeCode());
    }
}
