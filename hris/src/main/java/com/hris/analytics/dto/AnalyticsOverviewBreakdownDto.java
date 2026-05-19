package com.hris.analytics.dto;

public record AnalyticsOverviewBreakdownDto(
    String key,
    String label,
    long value,
    int percentage,
    String detail
) {
}
