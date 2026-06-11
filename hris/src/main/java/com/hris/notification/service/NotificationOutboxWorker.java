package com.hris.notification.service;

import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.repository.NotificationEventRepository;
import com.hris.tenancy.TenantJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Retries outbox rows whose immediate after-commit processing failed.
 *
 * <p>Deliberately NOT transactional: each event is processed in its own transaction
 * ({@link NotificationEventProcessor} runs REQUIRES_NEW), so one poison event cannot roll
 * back the batch or wedge the worker. After {@code max-attempts} failures an event is
 * stamped {@code failedAt} and excluded from future runs — the dead-letter equivalent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxWorker {

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationEventProcessor notificationEventProcessor;
    private final TenantJobRunner tenantJobRunner;

    @Value("${app.notification.outbox.max-attempts:10}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${app.notification.outbox.interval-ms:30000}")
    @SchedulerLock(name = "notificationOutboxWorker", lockAtMostFor = "PT5M", lockAtLeastFor = "PT25S")
    public void retryUndelivered() {
        tenantJobRunner.forEachActiveTenant("notificationOutboxWorker", tenant -> retryUndeliveredForCurrentTenant());
    }

    private void retryUndeliveredForCurrentTenant() {
        Instant cutoff = Instant.now().minusSeconds(60);
        List<NotificationEvent> pending = notificationEventRepository.findUndeliveredBefore(cutoff);

        if (pending.isEmpty()) return;

        log.info("Outbox worker: {} undelivered notification(s) to retry", pending.size());

        for (NotificationEvent event : pending) {
            try {
                notificationEventProcessor.process(event);
                log.debug("Outbox worker: delivered eventId={}", event.getId());
            } catch (Exception e) {
                registerFailedAttempt(event, e);
            }
        }
    }

    private void registerFailedAttempt(NotificationEvent event, Exception cause) {
        event.setAttempts(event.getAttempts() + 1);
        if (event.getAttempts() >= maxAttempts) {
            event.setFailedAt(Instant.now());
            log.error("Outbox worker: eventId={} type={} exhausted {} attempts and was dead-lettered",
                event.getId(), event.getEventType(), event.getAttempts(), cause);
        } else {
            log.warn("Outbox worker: retry {}/{} failed for eventId={}, will try again next cycle",
                event.getAttempts(), maxAttempts, event.getId(), cause);
        }
        notificationEventRepository.save(event);
    }
}
