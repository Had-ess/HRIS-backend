package com.hris.notification.dto;
import java.time.Instant;
import java.util.UUID;
public record NotificationResponseDto(
    UUID id,
    UUID userId,
    String title,
    String body,
    String linkPath,
    boolean isRead,
    Instant createdAt
) {}
