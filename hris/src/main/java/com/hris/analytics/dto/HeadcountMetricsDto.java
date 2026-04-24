package com.hris.analytics.dto;

public record HeadcountMetricsDto(
    long totalEmployees,
    long activeEmployees,
    long newHires,
    long terminatedEmployees
) {}
