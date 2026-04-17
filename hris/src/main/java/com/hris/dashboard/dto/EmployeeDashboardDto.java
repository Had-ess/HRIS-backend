package com.hris.dashboard.dto;

import java.util.List;

public record EmployeeDashboardDto(
    long unreadNotificationsCount,
    List<LeaveBalanceSummaryDto> leaveBalances,
    List<LeaveRequestSummaryDto> recentLeaveRequests,
    List<AdminRequestSummaryDto> recentAdminRequests
) {}
