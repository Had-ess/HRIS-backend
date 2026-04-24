package com.hris.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.config.RabbitMQConfig;
import com.hris.notification.entity.Notification;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GAP-B-11/12 FIXED:
 * - Uses user.getLocalePreference() instead of event.getLocale()
 * - Deserializes params as a Map and converts to Object[] for MessageSource
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_LEAVE)
    public void onLeaveEvent(NotificationEvent event) {
        processEvent(event);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ADMIN)
    public void onAdminEvent(NotificationEvent event) {
        processEvent(event);
    }

    private void processEvent(NotificationEvent event) {
        try {
            User user = userRepository.findById(event.getTargetUserId())
                .orElse(null);

            if (user == null) {
                log.warn("Target user not found for notification event: {}", event.getEventType());
                return;
            }

            // GAP-B-11: Use user's locale preference, not event's locale
            Locale locale = Locale.forLanguageTag(
                user.getLocalePreference() != null ? user.getLocalePreference() : "fr");

            Object[] paramsArray = deserializeParams(event);

            String title = messageSource.getMessage(event.getTitleKey(), paramsArray, locale);
            String body = messageSource.getMessage(event.getBodyKey(), paramsArray, locale);

            Notification notification = Notification.builder()
                .userId(user.getId())
                .title(title)
                .body(body)
                .linkPath(extractLinkPath(event))
                .isRead(false)
                .createdAt(Instant.now())
                .build();

            notificationRepository.save(notification);
            log.debug("Notification created for user: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to process notification event: {}", event.getEventType(), e);
            throw new IllegalStateException("Failed to process notification event " + event.getEventType(), e);
        }
    }

    /**
     * Converts JSON params to Object[].
     * Supports both Map and List formats.
     */
    private Object[] deserializeParams(NotificationEvent event) {
        String paramsJson = event.getParams();
        if (paramsJson == null || paramsJson.isBlank()) {
            return new Object[0];
        }
        try {
            Map<String, Object> map = objectMapper.readValue(paramsJson,
                new TypeReference<Map<String, Object>>() {});
            return mapParamsForEvent(event.getEventType(), map);
        } catch (Exception e) {
            try {
                List<Object> list = objectMapper.readValue(paramsJson,
                    new TypeReference<List<Object>>() {});
                return list.toArray();
            } catch (Exception ex) {
                log.warn("Failed to deserialize notification params: {}", paramsJson);
                return new Object[]{paramsJson};
            }
        }
    }

    private Object[] mapParamsForEvent(com.hris.notification.enums.NotificationEventType eventType,
                                        Map<String, Object> map) {
        return switch (eventType) {
            case ADMIN_REQUEST_SUBMITTED -> new Object[]{
                map.getOrDefault("requesterName", ""),
                map.getOrDefault("trackingNumber", ""),
                map.getOrDefault("requestType", "")
            };
            case ADMIN_REQUEST_PROCESSED -> new Object[]{
                map.getOrDefault("trackingNumber", "")
            };
            case PROJECT_ASSIGNED -> new Object[] {
                map.getOrDefault("projectName", "")
            };
            default -> new Object[]{
                map.getOrDefault("employeeName", ""),
                map.getOrDefault("startDate", ""),
                map.getOrDefault("endDate", ""),
                map.getOrDefault("workingDays", "")
            };
        };
    }

    private String extractLinkPath(NotificationEvent event) {
        String paramsJson = event.getParams();
        if (paramsJson == null || paramsJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(paramsJson,
                new TypeReference<Map<String, Object>>() {});
            Object value = map.get("targetPath");
            if (value instanceof String path && !path.isBlank()) {
                return path;
            }
        } catch (Exception ex) {
            log.debug("Notification event does not expose a link path for {}", event.getEventType(), ex);
        }
        return null;
    }
}
