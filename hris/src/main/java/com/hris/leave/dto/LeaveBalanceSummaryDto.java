package com.hris.leave.dto;

import java.math.BigDecimal;
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
    BigDecimal totalDays,
    BigDecimal usedDays,
    BigDecimal pendingDays,
    BigDecimal carryOverDays,
    BigDecimal availableDays
) {
}
