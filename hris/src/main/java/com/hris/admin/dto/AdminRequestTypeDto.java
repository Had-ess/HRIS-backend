package com.hris.admin.dto;
import java.time.Instant;
import java.util.UUID;
public record AdminRequestTypeDto(
    UUID id,
    String code,
    String name,
    String description,
    boolean requiresAttachment,
    Integer slaHours,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}
