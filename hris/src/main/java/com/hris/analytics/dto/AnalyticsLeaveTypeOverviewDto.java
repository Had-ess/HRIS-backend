package com.hris.analytics.dto;

public record AnalyticsLeaveTypeOverviewDto(
    String key,
    String label,
    long days,
    int percentage,
    String color
) {
}
