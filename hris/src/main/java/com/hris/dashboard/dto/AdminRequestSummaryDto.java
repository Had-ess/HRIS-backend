package com.hris.dashboard.dto;

import com.hris.admin.enums.AdminRequestStatus;
import com.hris.leave.enums.UrgencyLevel;

import java.time.Instant;
import java.util.UUID;

public record AdminRequestSummaryDto(
    UUID id,
    UUID requestTypeId,
    String requestTypeName,
    String trackingNumber,
    AdminRequestStatus status,
    UrgencyLevel urgencyLevel,
    Instant submittedAt
) {}
