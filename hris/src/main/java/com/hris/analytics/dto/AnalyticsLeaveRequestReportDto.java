package com.hris.analytics.dto;

import java.util.List;

public record AnalyticsLeaveRequestReportDto(
    long totalRequests,
    long approvedCount,
    long rejectedCount,
    long pendingCount,
    double averageProcessingDays,
    List<AnalyticsCountDto> byStatus,
    List<AnalyticsCountDto> byLeaveType,
    List<AnalyticsCountDto> byDepartment,
    List<AnalyticsDateCountDto> overTime
) {
}
