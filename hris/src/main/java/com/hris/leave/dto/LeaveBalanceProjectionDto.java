package com.hris.leave.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Projection of leave balances through the end of the current year.
 * Returned by GET /api/leave-balances/me/projection.
 */
public record LeaveBalanceProjectionDto(
    int year,
    List<LeaveTypeProjection> projections
) {
    public record LeaveTypeProjection(
        String leaveTypeCode,
        String leaveTypeName,
        BigDecimal currentBalance,
        BigDecimal estimatedAccrual,
        BigDecimal pendingDeductions,
        BigDecimal projectedBalance
    ) {}
}
