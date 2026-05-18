package com.hris.notification.dto;
import com.hris.notification.enums.NotificationType;
import java.time.Instant;
import java.util.UUID;
public record NotificationResponseDto(
    UUID id,
    UUID userId,
    String title,
    String body,
    String linkPath,
    NotificationType type,
    String actorDisplayName,
    boolean isRead,
    Instant createdAt
) {}
