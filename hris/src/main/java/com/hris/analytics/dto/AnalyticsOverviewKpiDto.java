package com.hris.analytics.dto;

public record AnalyticsOverviewKpiDto(
    String key,
    String label,
    String value,
    String delta,
    String icon,
    String tone
) {
}
