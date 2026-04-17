package com.hris.leave.dto;

import java.util.UUID;

public record LeaveBalanceDto(
    UUID id, UUID employeeId, UUID leaveTypeId,
    int year, int totalDays, int usedDays,
    int pendingDays, int carryOverDays, int availableDays
) {}
