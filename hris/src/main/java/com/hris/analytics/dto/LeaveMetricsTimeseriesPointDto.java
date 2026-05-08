package com.hris.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LeaveMetricsTimeseriesPointDto(
    LocalDate snapshotDate,
    int totalRequests,
    int approvedCount,
    int rejectedCount,
    int pendingCount,
    BigDecimal averageProcessingDays
) {
}
