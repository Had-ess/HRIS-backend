package com.hris.analytics.dto;

public record LeaveMetricsDto(
    long totalRequests,
    long approvedCount,
    long rejectedCount,
    double averageProcessingDays
) {}
