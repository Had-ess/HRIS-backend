package com.hris.analytics.dto;

import java.time.LocalDate;

public record AnalyticsDateCountDto(
    LocalDate date,
    long value
) {
}
