package com.hris.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaveBalanceSummaryDto(
    UUID leaveTypeId,
    String leaveTypeName,
    BigDecimal totalDays,
    BigDecimal usedDays,
    BigDecimal pendingDays,
    BigDecimal availableDays
) {}
