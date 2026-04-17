package com.hris.notification.dto;
import java.time.Instant;
import java.util.UUID;
public record NotificationResponseDto(UUID id, UUID userId, String title, String body, boolean isRead, Instant createdAt) {}
