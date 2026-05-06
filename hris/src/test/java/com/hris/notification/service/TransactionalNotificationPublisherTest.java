package com.hris.notification.service;

import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionalNotificationPublisher Unit Tests")
class TransactionalNotificationPublisherTest {

    @Mock
    private NotificationPublisher notificationPublisher;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("publishes immediately when no transaction is active")
    void publishesImmediatelyWhenNoTransactionIsActive() {
        TransactionalNotificationPublisher publisher =
            new TransactionalNotificationPublisher(notificationPublisher);
        NotificationEvent event = buildEvent();

        publisher.publishAfterCommit(event);

        verify(notificationPublisher).publish(event);
    }

    @Test
    @DisplayName("defers publishing until the current transaction commits")
    void defersPublishingUntilTransactionCommits() {
        TransactionalNotificationPublisher publisher =
            new TransactionalNotificationPublisher(notificationPublisher);
        NotificationEvent event = buildEvent();

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publishAfterCommit(event);

        verify(notificationPublisher, never()).publish(event);

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(notificationPublisher).publish(event);
    }

    private NotificationEvent buildEvent() {
        return NotificationEvent.builder()
            .eventType(NotificationEventType.LEAVE_APPROVED)
            .targetUserId(UUID.randomUUID())
            .titleKey("leave.approved.title")
            .bodyKey("leave.approved.body")
            .params("{}")
            .locale("en")
            .routingKey("leave.approved")
            .publishedAt(Instant.now())
            .build();
    }
}
