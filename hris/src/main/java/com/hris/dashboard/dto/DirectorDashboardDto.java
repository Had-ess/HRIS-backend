package com.hris.dashboard.dto;

public record DirectorDashboardDto(
    long totalEmployees,
    long totalDepartments,
    long activeProjectsCount,
    long pendingApprovalsCount,
    LeaveMetricsSummaryDto currentPeriodLeaveMetrics,
    long pendingAdminRequestsCount
) {}
