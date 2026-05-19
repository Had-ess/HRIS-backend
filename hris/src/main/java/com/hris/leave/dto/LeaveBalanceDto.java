package com.hris.leave.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaveBalanceDto(
    UUID id, UUID employeeId, UUID leaveTypeId, String leaveTypeCode, String leaveTypeName,
    int year, BigDecimal totalDays, BigDecimal usedDays,
    BigDecimal pendingDays, BigDecimal carryOverDays, BigDecimal availableDays
) {}
