package com.hris.analytics.dto;

public record AnalyticsCountDto(
    String key,
    String label,
    long value
) {
}
