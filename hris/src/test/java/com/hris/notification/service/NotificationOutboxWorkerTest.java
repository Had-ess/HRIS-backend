package com.hris.notification.service;

import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.repository.NotificationEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.amqp.core.MessagePostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationOutboxWorker Unit Tests")
class NotificationOutboxWorkerTest {

    @Mock
    private NotificationEventRepository notificationEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NotificationOutboxWorker worker;

    @Test
    @DisplayName("retryUndelivered marks event as delivered when AMQP publish succeeds")
    void retryUndeliveredMarksEventAsDeliveredOnSuccess() {
        NotificationEvent event = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(NotificationEventType.LEAVE_SUBMITTED)
            .targetUserId(UUID.randomUUID())
            .titleKey("leave.submitted.title")
            .bodyKey("leave.submitted.body")
            .params("{}")
            .locale("fr")
            .routingKey("leave.submitted")
            .publishedAt(Instant.now().minusSeconds(120))
            .build();

        when(notificationEventRepository.findUndeliveredBefore(any(Instant.class)))
            .thenReturn(List.of(event));

        worker.retryUndelivered();

        assertThat(event.getDeliveredAt()).isNotNull();
        verify(notificationEventRepository).save(event);
    }

    @Test
    @DisplayName("retryUndelivered leaves deliveredAt null when AMQP publish fails")
    void retryUndeliveredLeavesDeliveredAtNullOnAmqpFailure() {
        NotificationEvent event = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(NotificationEventType.LEAVE_APPROVED)
            .targetUserId(UUID.randomUUID())
            .titleKey("leave.approved.title")
            .bodyKey("leave.approved.body")
            .params("{}")
            .locale("fr")
            .routingKey("leave.approved")
            .publishedAt(Instant.now().minusSeconds(120))
            .build();

        when(notificationEventRepository.findUndeliveredBefore(any(Instant.class)))
            .thenReturn(List.of(event));
        doThrow(new AmqpException("broker unavailable"))
            .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), eq(event), any(MessagePostProcessor.class));

        worker.retryUndelivered();

        assertThat(event.getDeliveredAt()).isNull();
    }
}
