package com.hris.dashboard.dto;

import java.util.UUID;

public record LeaveBalanceSummaryDto(
    UUID leaveTypeId,
    String leaveTypeName,
    int totalDays,
    int usedDays,
    int pendingDays,
    int availableDays
) {}
