package com.hris.analytics.dto;

public record AnalyticsSummaryDto(
    long leaveRequests,
    long pendingLeaveRequests,
    long pendingApprovals,
    long pendingAdminRequests,
    long availableBalanceDays,
    long negativeBalances,
    long slaExceededAdminRequests
) {
}
