package com.hris.leave.dto;

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
        /** Current available days */
        int currentBalance,
        /** Estimated accruals remaining through year-end */
        int estimatedAccrual,
        /** Approved + pending leave days not yet deducted */
        int pendingDeductions,
        /** Projected balance = current + estimated accrual - pending */
        int projectedBalance
    ) {}
}
