package com.hris.analytics.dto;

import com.hris.analytics.enums.AnalyticsScopeType;

import java.util.UUID;

public record AnalyticsDashboardDto(
    AnalyticsScopeType scopeType,
    UUID scopeId,
    String scopeLabel,
    long employeesInScope,
    long leaveRequests,
    long pendingApprovals,
    long pendingAdminRequests,
    long availableBalanceDays
) {
}
