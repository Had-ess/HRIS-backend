package com.hris.notification.service;

import com.hris.config.RabbitMQConfig;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.repository.NotificationEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPublisher Unit Tests")
class NotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private MessageConverter messageConverter;

    @Mock
    private NotificationEventRepository notificationEventRepository;

    @Test
    @DisplayName("publish persists the event before sending it to RabbitMQ")
    void publishPersistsTheEventBeforeSendingItToRabbitMq() {
        NotificationPublisher publisher = new NotificationPublisher(
            rabbitTemplate,
            messageConverter,
            notificationEventRepository
        );
        NotificationEvent event = NotificationEvent.builder()
            .eventType(NotificationEventType.ADMIN_REQUEST_SUBMITTED)
            .targetUserId(UUID.randomUUID())
            .titleKey("admin.submitted.title")
            .bodyKey("admin.submitted.body")
            .params("{\"requesterName\":\"Ali\"}")
            .locale("fr")
            .routingKey("admin.submitted")
            .publishedAt(Instant.now())
            .build();
        NotificationEvent saved = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(event.getEventType())
            .targetUserId(event.getTargetUserId())
            .titleKey(event.getTitleKey())
            .bodyKey(event.getBodyKey())
            .params(event.getParams())
            .locale(event.getLocale())
            .routingKey(event.getRoutingKey())
            .publishedAt(event.getPublishedAt())
            .build();

        when(notificationEventRepository.save(event)).thenReturn(saved);

        publisher.publish(event);

        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(notificationEventRepository).save(event);
        verify(rabbitTemplate).convertAndSend(
            eq(RabbitMQConfig.EXCHANGE),
            eq("admin.submitted"),
            eq(saved),
            any(),
            correlationCaptor.capture()
        );
        assertThat(correlationCaptor.getValue().getId()).isEqualTo(saved.getId().toString());
    }

    @Test
    @DisplayName("publish swallows RabbitMQ failures after persisting the event")
    void publishSwallowsRabbitMqFailuresAfterPersistingTheEvent() {
        NotificationPublisher publisher = new NotificationPublisher(
            rabbitTemplate,
            messageConverter,
            notificationEventRepository
        );
        NotificationEvent event = NotificationEvent.builder()
            .eventType(NotificationEventType.LEAVE_APPROVED)
            .targetUserId(UUID.randomUUID())
            .titleKey("leave.approved.title")
            .bodyKey("leave.approved.body")
            .params("[\"Ali Ben\",\"2026-05-01\",\"2026-05-05\",3]")
            .locale("fr")
            .routingKey("leave.approved")
            .publishedAt(Instant.now())
            .build();
        NotificationEvent saved = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(event.getEventType())
            .targetUserId(event.getTargetUserId())
            .titleKey(event.getTitleKey())
            .bodyKey(event.getBodyKey())
            .params(event.getParams())
            .locale(event.getLocale())
            .routingKey(event.getRoutingKey())
            .publishedAt(event.getPublishedAt())
            .build();

        when(notificationEventRepository.save(event)).thenReturn(saved);
        doThrow(new RuntimeException("broker unavailable")).when(rabbitTemplate).convertAndSend(
            eq(RabbitMQConfig.EXCHANGE),
            eq("leave.approved"),
            eq(saved),
            any(),
            any(CorrelationData.class)
        );

        publisher.publish(event);

        verify(notificationEventRepository).save(event);
        verify(rabbitTemplate).convertAndSend(
            eq(RabbitMQConfig.EXCHANGE),
            eq("leave.approved"),
            eq(saved),
            any(),
            any(CorrelationData.class)
        );
        verify(rabbitTemplate, never()).send(any(Message.class));
    }

    @Test
    @DisplayName("sendNow sends an already-persisted event and stamps deliveredAt without re-inserting")
    void sendNowSendsAlreadyPersistedEventAndStampsDeliveredAt() {
        NotificationPublisher publisher = new NotificationPublisher(
            rabbitTemplate,
            messageConverter,
            notificationEventRepository
        );
        NotificationEvent saved = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(NotificationEventType.LEAVE_APPROVED)
            .targetUserId(UUID.randomUUID())
            .titleKey("leave.approved.title")
            .bodyKey("leave.approved.body")
            .params("{}")
            .locale("fr")
            .routingKey("leave.approved")
            .publishedAt(Instant.now())
            .build();

        publisher.sendNow(saved);

        // Sent to the broker, then stamped delivered and saved once (the delivery stamp only).
        verify(rabbitTemplate).convertAndSend(
            eq(RabbitMQConfig.EXCHANGE),
            eq("leave.approved"),
            eq(saved),
            any(),
            any(CorrelationData.class)
        );
        assertThat(saved.getDeliveredAt()).isNotNull();
        verify(notificationEventRepository).save(saved);
    }

    @Test
    @DisplayName("sendNow leaves deliveredAt null when the broker send fails")
    void sendNowLeavesDeliveredAtNullOnFailure() {
        NotificationPublisher publisher = new NotificationPublisher(
            rabbitTemplate,
            messageConverter,
            notificationEventRepository
        );
        NotificationEvent saved = NotificationEvent.builder()
            .id(UUID.randomUUID())
            .eventType(NotificationEventType.LEAVE_APPROVED)
            .targetUserId(UUID.randomUUID())
            .titleKey("leave.approved.title")
            .bodyKey("leave.approved.body")
            .params("{}")
            .locale("fr")
            .routingKey("leave.approved")
            .publishedAt(Instant.now())
            .build();
        doThrow(new RuntimeException("broker unavailable")).when(rabbitTemplate).convertAndSend(
            eq(RabbitMQConfig.EXCHANGE),
            eq("leave.approved"),
            eq(saved),
            any(),
            any(CorrelationData.class)
        );

        publisher.sendNow(saved);

        // Failure is swallowed; the row keeps deliveredAt == null for the outbox worker to retry.
        assertThat(saved.getDeliveredAt()).isNull();
        verify(notificationEventRepository, never()).save(saved);
    }
}
