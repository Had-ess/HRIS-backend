package com.hris.notification.service;

import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional outbox entry point for notifications.
 *
 * <p>The {@link NotificationEvent} row is written in the <em>same</em> transaction as the
 * business change, so it commits atomically with it — a crash between the business commit
 * and processing can never drop a notification. Once the transaction commits, an
 * after-commit hook processes the event immediately (low latency); if that fails, the row
 * stays {@code deliveredAt == null} and {@link NotificationOutboxWorker} retries it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionalNotificationPublisher {

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationEventProcessor notificationEventProcessor;

    public void publishAfterCommit(NotificationEvent event) {
        // Persist within the caller's transaction (if any) — atomic with the business change.
        NotificationEvent saved = notificationEventRepository.save(event);

        if (TransactionSynchronizationManager.isSynchronizationActive()
            && TransactionSynchronizationManager.isActualTransactionActive()) {
            // Best-effort low-latency processing once that transaction commits.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    processQuietly(saved);
                }
            });
            return;
        }

        // No active transaction (e.g. scheduled jobs without one): process inline.
        processQuietly(saved);
    }

    private void processQuietly(NotificationEvent saved) {
        try {
            notificationEventProcessor.process(saved);
        } catch (Exception e) {
            // Fire-and-forget: never let notification failures break business flows.
            // deliveredAt remains null; the outbox worker will retry.
            log.warn("Notification processing failed, will be retried by outbox worker. eventId={}",
                saved.getId(), e);
        }
    }
}
