package com.hris.leave.dto;

import java.util.UUID;

public record LeaveBalanceSummaryDto(
    UUID balanceId,
    UUID employeeId,
    String employeeCode,
    UUID userId,
    String employeeFirstName,
    String employeeLastName,
    UUID leaveTypeId,
    String leaveTypeCode,
    String leaveTypeName,
    int year,
    int totalDays,
    int usedDays,
    int pendingDays,
    int carryOverDays,
    int availableDays
) {
}
