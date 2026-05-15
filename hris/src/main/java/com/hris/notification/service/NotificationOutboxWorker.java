package com.hris.notification.service;

import com.hris.config.RabbitMQConfig;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxWorker {

    private final NotificationEventRepository notificationEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${app.notification.outbox.interval-ms:30000}")
    @SchedulerLock(name = "notificationOutboxWorker", lockAtMostFor = "PT5M", lockAtLeastFor = "PT25S")
    @Transactional
    public void retryUndelivered() {
        Instant cutoff = Instant.now().minusSeconds(60);
        List<NotificationEvent> pending = notificationEventRepository.findUndeliveredBefore(cutoff);

        if (pending.isEmpty()) return;

        log.info("Outbox worker: {} undelivered notification(s) to retry", pending.size());

        for (NotificationEvent event : pending) {
            try {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    event.getRoutingKey(),
                    event,
                    message -> {
                        message.getMessageProperties().setMessageId(event.getId().toString());
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    }
                );
                event.setDeliveredAt(Instant.now());
                notificationEventRepository.save(event);
                log.debug("Outbox worker: delivered eventId={}", event.getId());
            } catch (Exception e) {
                log.warn("Outbox worker: retry failed for eventId={}, will try again next cycle",
                    event.getId(), e);
            }
        }
    }
}
