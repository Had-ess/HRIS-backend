package com.hris.notification.service;

import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.repository.NotificationEventRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionalNotificationPublisher Unit Tests")
class TransactionalNotificationPublisherTest {

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private NotificationEventRepository notificationEventRepository;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("persists + sends inline when no transaction is active")
    void publishesImmediatelyWhenNoTransactionIsActive() {
        TransactionalNotificationPublisher publisher =
            new TransactionalNotificationPublisher(notificationPublisher, notificationEventRepository);
        NotificationEvent event = buildEvent();

        publisher.publishAfterCommit(event);

        // No transaction → fall back to the inline persist-and-send path.
        verify(notificationPublisher).publish(event);
        verify(notificationEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("persists the event in the current transaction and sends only after commit")
    void persistsInTransactionThenSendsAfterCommit() {
        TransactionalNotificationPublisher publisher =
            new TransactionalNotificationPublisher(notificationPublisher, notificationEventRepository);
        NotificationEvent event = buildEvent();
        NotificationEvent saved = buildEvent();
        when(notificationEventRepository.save(event)).thenReturn(saved);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publishAfterCommit(event);

        // The row is written atomically with the business change, before commit...
        verify(notificationEventRepository).save(event);
        // ...but nothing is sent to the broker until the transaction commits.
        verify(notificationPublisher, never()).sendNow(any());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(notificationPublisher).sendNow(saved);
        // The inline persist-and-send path is never used inside a transaction.
        verify(notificationPublisher, never()).publish(any());
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
