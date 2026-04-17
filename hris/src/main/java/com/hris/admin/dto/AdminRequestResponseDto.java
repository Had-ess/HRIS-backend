package com.hris.admin.dto;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.leave.enums.UrgencyLevel;
import java.time.Instant;
import java.util.UUID;
public record AdminRequestResponseDto(
    UUID id, UUID requesterId, UUID requestTypeId, String trackingNumber,
    String description, UrgencyLevel urgencyLevel, AdminRequestStatus status,
    String metadata, Instant submittedAt, Instant resolvedAt, UUID resolvedById
) {}
