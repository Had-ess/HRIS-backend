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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionalNotificationPublisher Unit Tests")
class TransactionalNotificationPublisherTest {

    @Mock
    private NotificationEventProcessor notificationEventProcessor;

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
    @DisplayName("persists + processes inline when no transaction is active")
    void processesImmediatelyWhenNoTransactionIsActive() {
        TransactionalNotificationPublisher publisher =
            new TransactionalNotificationPublisher(notificationEventRepository, notificationEventProcessor);
        NotificationEvent event = buildEvent();
        NotificationEvent saved = buildEvent();
        when(notificationEventRepository.save(event)).thenReturn(saved);

        publisher.publishAfterCommit(event);

        verify(notificationEventRepository).save(event);
        verify(notificationEventProcessor).process(saved);
    }

    @Test
    @DisplayName("persists the event in the current transaction and processes only after commit")
    void persistsInTransactionThenProcessesAfterCommit() {
        TransactionalNotificationPublisher publisher =
            new TransactionalNotificationPublisher(notificationEventRepository, notificationEventProcessor);
        NotificationEvent event = buildEvent();
        NotificationEvent saved = buildEvent();
        when(notificationEventRepository.save(event)).thenReturn(saved);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publishAfterCommit(event);

        // The row is written atomically with the business change, before commit...
        verify(notificationEventRepository).save(event);
        // ...but nothing is processed until the transaction commits.
        verify(notificationEventProcessor, never()).process(any());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(notificationEventProcessor).process(saved);
    }

    @Test
    @DisplayName("processing failures are swallowed so business flows never break")
    void processingFailuresAreSwallowed() {
        TransactionalNotificationPublisher publisher =
            new TransactionalNotificationPublisher(notificationEventRepository, notificationEventProcessor);
        NotificationEvent event = buildEvent();
        NotificationEvent saved = buildEvent();
        when(notificationEventRepository.save(event)).thenReturn(saved);
        doThrow(new IllegalStateException("processing failed"))
            .when(notificationEventProcessor).process(saved);

        // The outbox row is persisted; the worker will retry the failed processing.
        assertThatCode(() -> publisher.publishAfterCommit(event)).doesNotThrowAnyException();
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
