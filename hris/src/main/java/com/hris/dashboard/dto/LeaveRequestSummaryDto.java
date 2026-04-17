package com.hris.dashboard.dto;

import com.hris.leave.enums.LeaveStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestSummaryDto(
    UUID id,
    UUID leaveTypeId,
    String leaveTypeName,
    LocalDate startDate,
    LocalDate endDate,
    int workingDays,
    LeaveStatus status,
    Instant submittedAt
) {}
