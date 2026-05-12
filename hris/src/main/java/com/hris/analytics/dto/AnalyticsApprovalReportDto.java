package com.hris.analytics.dto;

import java.util.List;

public record AnalyticsApprovalReportDto(
    long workflowTotal,
    long workflowPending,
    long workflowApproved,
    long workflowRejected,
    long stepPending,
    long stepApproved,
    long stepRejected,
    double averageApprovalHours,
    long overdueCount,
    List<AnalyticsApprovalBottleneckDto> bottlenecks
) {
}
