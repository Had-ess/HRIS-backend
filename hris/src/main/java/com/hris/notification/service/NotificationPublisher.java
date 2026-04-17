package com.hris.notification.service;

import com.hris.config.RabbitMQConfig;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final NotificationEventRepository notificationEventRepository;

    public void publish(NotificationEvent event) {
        try {
            // Persist event first for audit trail
            NotificationEvent saved = notificationEventRepository.save(event);
            // Then publish to RabbitMQ
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, event.getRoutingKey(), saved);
            log.debug("Published notification event: {}", event.getEventType());
        } catch (Exception e) {
            // Fire-and-forget: never let notification failures break business flows
            log.error("CRITICAL: Failed to publish notification event: {}", event.getEventType(), e);
        }
    }
}
