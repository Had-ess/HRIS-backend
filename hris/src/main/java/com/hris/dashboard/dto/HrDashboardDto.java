package com.hris.dashboard.dto;

import java.util.List;

public record HrDashboardDto(
    long pendingApprovalsCount,
    long pendingAdminRequestsCount,
    long totalEmployees,
    long totalDepartments,
    List<AdminRequestSummaryDto> recentAdminRequests
) {}
