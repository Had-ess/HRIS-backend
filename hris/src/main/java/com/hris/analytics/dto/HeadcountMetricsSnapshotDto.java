package com.hris.analytics.dto;

public record HeadcountMetricsSnapshotDto(
    int totalEmployees,
    int activeEmployees,
    int newHires,
    int terminatedEmployees
) {
}
