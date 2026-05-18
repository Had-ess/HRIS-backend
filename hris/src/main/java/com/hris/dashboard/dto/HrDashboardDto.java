package com.hris.dashboard.dto;

import java.util.List;

public record HrDashboardDto(
    long pendingApprovalsCount,
    long pendingAdminRequestsCount,
    long totalEmployees,
    long totalDepartments,
    long onLeaveToday,
    long onboardingCount,
    List<AdminRequestSummaryDto> recentAdminRequests,
    List<MonthlyCountDto> headcountTrend,
    List<LeaveTypeDistributionDto> leaveDistribution
) {
    public record MonthlyCountDto(String month, long count) {}
    public record LeaveTypeDistributionDto(String leaveTypeName, String leaveTypeCode, long totalDays) {}
}
