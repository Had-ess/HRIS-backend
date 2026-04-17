package com.hris.dashboard.dto;

public record LeaveMetricsSummaryDto(
    String period,
    int totalRequests,
    int approvedCount,
    int rejectedCount,
    double avgProcessingDays
) {}
