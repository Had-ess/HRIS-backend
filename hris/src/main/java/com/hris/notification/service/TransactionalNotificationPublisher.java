package com.hris.notification.service;

import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional outbox entry point for notifications.
 *
 * <p>The {@link NotificationEvent} row is written in the <em>same</em> transaction as the
 * business change, so it commits atomically with it — a crash between the business commit and
 * the broker call can no longer drop the notification. Once the transaction commits, an
 * after-commit hook performs a best-effort immediate send (low latency); if that send fails,
 * the row stays {@code deliveredAt == null} and {@link NotificationOutboxWorker} relays it.
 */
@Service
@RequiredArgsConstructor
public class TransactionalNotificationPublisher {

    private final NotificationPublisher notificationPublisher;
    private final NotificationEventRepository notificationEventRepository;

    public void publishAfterCommit(NotificationEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
            && TransactionSynchronizationManager.isActualTransactionActive()) {
            // Persist within the caller's transaction — atomic with the business change.
            NotificationEvent saved = notificationEventRepository.save(event);
            // Best-effort low-latency delivery once that transaction commits.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationPublisher.sendNow(saved);
                }
            });
            return;
        }

        // No active transaction (e.g. scheduled jobs without one): persist + send inline.
        notificationPublisher.publish(event);
    }
}
