package com.hris.analytics.dto;

import com.hris.analytics.enums.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
    UUID id,
    UUID actorId,
    String actorName,
    AuditAction action,
    String resource,
    UUID resourceId,
    String previousState,
    String newState,
    String ipAddress,
    Instant timestamp
) {}
