package com.hris.notification.service;

import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.repository.NotificationEventRepository;
import com.hris.tenancy.Tenant;
import com.hris.tenancy.TenantJobRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationOutboxWorker Unit Tests")
class NotificationOutboxWorkerTest {

    @Mock
    private NotificationEventRepository notificationEventRepository;

    @Mock
    private NotificationEventProcessor notificationEventProcessor;

    @Mock
    private TenantJobRunner tenantJobRunner;

    @InjectMocks
    private NotificationOutboxWorker worker;

    @BeforeEach
    void configureWorker() {
        ReflectionTestUtils.setField(worker, "maxAttempts", 3);
        // Run the per-tenant body once, as if a single active tenant existed.
        doAnswer(invocation -> {
            invocation.<Consumer<Tenant>>getArgument(1).accept(null);
            return null;
        }).when(tenantJobRunner).forEachActiveTenant(anyString(), any());
    }

    private NotificationEvent pendingEvent(NotificationEventType type, String routingKey) {
        return NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(type)
            .targetUserId(UUID.randomUUID())
            .titleKey("key.title")
            .bodyKey("key.body")
            .params("{}")
            .locale("fr")
            .routingKey(routingKey)
            .publishedAt(Instant.now().minusSeconds(120))
            .build();
    }

    @Test
    @DisplayName("retryUndelivered delegates pending events to the processor")
    void retryUndeliveredDelegatesToProcessor() {
        NotificationEvent event = pendingEvent(NotificationEventType.LEAVE_SUBMITTED, "leave.submitted");

        when(notificationEventRepository.findUndeliveredBefore(any(Instant.class)))
            .thenReturn(List.of(event));

        worker.retryUndelivered();

        verify(notificationEventProcessor).process(event);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getFailedAt()).isNull();
    }

    @Test
    @DisplayName("retryUndelivered increments attempts when processing fails")
    void retryUndeliveredIncrementsAttemptsOnFailure() {
        NotificationEvent event = pendingEvent(NotificationEventType.LEAVE_APPROVED, "leave.approved");

        when(notificationEventRepository.findUndeliveredBefore(any(Instant.class)))
            .thenReturn(List.of(event));
        doThrow(new IllegalStateException("processing failed"))
            .when(notificationEventProcessor).process(event);

        worker.retryUndelivered();

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getFailedAt()).isNull();
        assertThat(event.getDeliveredAt()).isNull();
        verify(notificationEventRepository).save(event);
    }

    @Test
    @DisplayName("retryUndelivered dead-letters the event after max attempts")
    void retryUndeliveredDeadLettersAfterMaxAttempts() {
        NotificationEvent event = pendingEvent(NotificationEventType.LEAVE_REJECTED, "leave.rejected");
        event.setAttempts(2); // next failure is attempt 3 of 3

        when(notificationEventRepository.findUndeliveredBefore(any(Instant.class)))
            .thenReturn(List.of(event));
        doThrow(new IllegalStateException("processing failed"))
            .when(notificationEventProcessor).process(event);

        worker.retryUndelivered();

        assertThat(event.getAttempts()).isEqualTo(3);
        assertThat(event.getFailedAt()).isNotNull();
        verify(notificationEventRepository).save(event);
    }

    @Test
    @DisplayName("one poison event does not prevent the rest of the batch from processing")
    void poisonEventDoesNotBlockBatch() {
        NotificationEvent poison = pendingEvent(NotificationEventType.LEAVE_SUBMITTED, "leave.submitted");
        NotificationEvent healthy = pendingEvent(NotificationEventType.LEAVE_APPROVED, "leave.approved");

        when(notificationEventRepository.findUndeliveredBefore(any(Instant.class)))
            .thenReturn(List.of(poison, healthy));
        doThrow(new IllegalStateException("processing failed"))
            .when(notificationEventProcessor).process(poison);

        worker.retryUndelivered();

        verify(notificationEventProcessor).process(healthy);
    }
}
