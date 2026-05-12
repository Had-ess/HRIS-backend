package com.hris.dashboard.dto;

import com.hris.admin.enums.AdminRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminRequestSummaryDto(
    UUID id,
    UUID requestTypeId,
    String requestTypeName,
    String requestNumber,
    AdminRequestStatus status,
    Instant submittedAt
) {}
