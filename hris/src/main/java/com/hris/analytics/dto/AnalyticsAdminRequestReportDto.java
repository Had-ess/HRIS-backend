package com.hris.analytics.dto;

import java.util.List;

public record AnalyticsAdminRequestReportDto(
    long totalRequests,
    long submittedCount,
    long inReviewCount,
    long completedCount,
    long rejectedCount,
    long pendingCount,
    double averageProcessingHours,
    long slaExceededCount,
    List<AnalyticsCountDto> byStatus,
    List<AnalyticsCountDto> byType,
    List<AnalyticsDateCountDto> overTime
) {
}
