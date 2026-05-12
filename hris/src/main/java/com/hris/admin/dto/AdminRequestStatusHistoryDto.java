package com.hris.admin.dto;

import com.hris.admin.enums.AdminRequestStatus;

import java.time.Instant;

public record AdminRequestStatusHistoryDto(
    AdminRequestStatus status,
    Instant occurredAt
) {
}
