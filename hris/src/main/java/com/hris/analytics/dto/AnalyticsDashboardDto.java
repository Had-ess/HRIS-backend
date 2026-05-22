package com.hris.analytics.dto;

public record AnalyticsDashboardDto(
    long employeesInScope,
    long leaveRequests,
    long pendingApprovals,
    long pendingAdminRequests,
    long availableBalanceDays
) {
}
