package com.hris.analytics.dto;

import java.math.BigDecimal;

public record LeaveMetricsSnapshotDto(
    int totalRequests,
    int approvedCount,
    int rejectedCount,
    int pendingCount,
    BigDecimal averageProcessingDays
) {
}
