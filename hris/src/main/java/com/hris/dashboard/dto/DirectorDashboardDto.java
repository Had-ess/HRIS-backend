package com.hris.dashboard.dto;

import java.util.List;

public record DirectorDashboardDto(
    long totalEmployees,
    long totalDepartments,
    long activeProjectsCount,
    long pendingApprovalsCount,
    LeaveMetricsSummaryDto currentPeriodLeaveMetrics,
    long pendingAdminRequestsCount,
    double attritionRate,
    double avgLeaveTakenDays,
    double approvalCycleHours,
    List<ApprovalBottleneckDto> bottlenecks,
    List<HeadcountByDeptDto> headcountByDept,
    List<RiskSignalDto> riskSignals,
    List<ProjectUtilizationDto> projectUtilization
) {
    public record ApprovalBottleneckDto(
        String stepName,
        int stepOrder,
        double avgHours,
        double medianHours,
        long pendingCount
    ) {}

    public record HeadcountByDeptDto(String deptName, long count) {}

    public record RiskSignalDto(
        String eventDescription,
        String actorName,
        String riskLevel,
        String occurredAt
    ) {}

    public record ProjectUtilizationDto(
        String projectName,
        int utilizationPct,
        long memberCount
    ) {}
}
