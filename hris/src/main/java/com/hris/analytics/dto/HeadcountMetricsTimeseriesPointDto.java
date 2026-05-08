package com.hris.analytics.dto;

import java.time.LocalDate;

public record HeadcountMetricsTimeseriesPointDto(
    LocalDate snapshotDate,
    int totalEmployees,
    int activeEmployees,
    int newHires,
    int terminatedEmployees
) {
}
