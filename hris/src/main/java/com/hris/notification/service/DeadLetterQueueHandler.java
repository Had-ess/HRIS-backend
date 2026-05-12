package com.hris.notification.service;

import com.hris.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Handles messages that end up in the dead-letter queue after exhausting retries.
 * Logs structured information for operational visibility.
 */
@Slf4j
@Service
public class DeadLetterQueueHandler {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DLQ)
    public void handleDeadLetter(Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String messageId = message.getMessageProperties().getMessageId();
        String deathReason = headers.containsKey("x-first-death-reason")
            ? headers.get("x-first-death-reason").toString() : "unknown";
        String deathQueue = headers.containsKey("x-first-death-queue")
            ? headers.get("x-first-death-queue").toString() : "unknown";

        String bodyPreview;
        try {
            byte[] body = message.getBody();
            bodyPreview = new String(body, 0, Math.min(body.length, 500), StandardCharsets.UTF_8);
        } catch (Exception e) {
            bodyPreview = "<unreadable>";
        }

        log.error("Dead letter received — messageId={}, routingKey={}, deathQueue={}, deathReason={}, body={}",
            messageId, routingKey, deathQueue, deathReason, bodyPreview);
    }
}
