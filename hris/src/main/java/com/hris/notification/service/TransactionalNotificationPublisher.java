package com.hris.notification.service;

import com.hris.notification.entity.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers notification side effects until database changes are committed.
 */
@Service
@RequiredArgsConstructor
public class TransactionalNotificationPublisher {

    private final NotificationPublisher notificationPublisher;

    public void publishAfterCommit(NotificationEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
            && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationPublisher.publish(event);
                }
            });
            return;
        }

        notificationPublisher.publish(event);
    }
}
