package com.hris.notification.service;

import com.hris.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service @Slf4j
public class DeadLetterQueueHandler {
    @RabbitListener(queues = RabbitMQConfig.QUEUE_DLQ)
    public void handleDeadLetter(Message message) {
        log.error("DLQ message received: {}", new String(message.getBody()));
        log.error("Headers: {}", message.getMessageProperties().getHeaders());
    }
}
