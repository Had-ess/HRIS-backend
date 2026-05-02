package com.hris.notification.service;

import com.hris.config.RabbitMQConfig;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.repository.NotificationEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;

/**
 * GAP-B-34 FIXED: Saves NotificationEvent before publishing to RabbitMQ.
 * Fire-and-forget pattern — swallows publish errors.
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

    public void publish(NotificationEvent event) {
        try {
            // Persist event first for audit trail
            NotificationEvent saved = notificationEventRepository.save(event);
            // Then publish to RabbitMQ
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
            log.debug("Published notification event {} with id {}", saved.getEventType(), saved.getId());
        } catch (Exception e) {
            // Fire-and-forget: never let notification failures break business flows
            log.error("CRITICAL: Failed to publish notification event: {}", event.getEventType(), e);
        }
    }
}
