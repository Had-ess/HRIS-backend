package com.hris.notification.service;

import com.hris.config.RabbitMQConfig;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.repository.NotificationEventRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;

/**
 * Relays {@link NotificationEvent} outbox rows to RabbitMQ.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #publish(NotificationEvent)} — persists the event, then sends it. Used only
 *       when no business transaction is active (e.g. the inline fallback path).</li>
 *   <li>{@link #sendNow(NotificationEvent)} — sends an event that is <em>already persisted</em>
 *       (the transactional-outbox fast path, invoked after the business transaction commits).</li>
 * </ul>
 * Both are fire-and-forget: AMQP failures are swallowed and {@code deliveredAt} is left null so
 * {@link NotificationOutboxWorker} can retry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final NotificationEventRepository notificationEventRepository;

    @PostConstruct
    void configureRabbitTemplate() {
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned ->
            log.error(
                "Notification message was returned by RabbitMQ. exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
            )
        );
        rabbitTemplate.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error(
                    "RabbitMQ did not acknowledge notification publish. correlationId={}, cause={}",
                    correlation != null ? correlation.getId() : null,
                    cause
                );
            }
        });
    }

    /**
     * Inline fallback: persist the event, then send it. Used when no business transaction is
     * active, so there is nothing to be atomic with. Within a transaction, callers persist the
     * event in that transaction and use {@link #sendNow(NotificationEvent)} after commit instead.
     */
    public void publish(NotificationEvent event) {
        // Persist event first — deliveredAt stays null until AMQP delivery confirmed
        NotificationEvent saved = notificationEventRepository.save(event);
        sendNow(saved);
    }

    /**
     * Sends an already-persisted outbox event to RabbitMQ and stamps {@code deliveredAt} on
     * success. The row must already exist (written in the business transaction); on failure the
     * row keeps {@code deliveredAt == null} and {@link NotificationOutboxWorker} relays it later.
     */
    public void sendNow(NotificationEvent saved) {
        try {
            CorrelationData correlationData = new CorrelationData(saved.getId().toString());
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                saved.getRoutingKey(),
                saved,
                message -> {
                    message.getMessageProperties().setMessageId(saved.getId().toString());
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlationData
            );
            saved.setDeliveredAt(Instant.now());
            notificationEventRepository.save(saved);
            log.debug("Published notification event {} with id {}", saved.getEventType(), saved.getId());
        } catch (Exception e) {
            // Fire-and-forget: never let notification failures break business flows.
            // deliveredAt remains null; the outbox worker will retry after 1 minute.
            log.warn("Notification delivery failed, will be retried by outbox worker. eventId={}", saved.getId(), e);
        }
    }
}
