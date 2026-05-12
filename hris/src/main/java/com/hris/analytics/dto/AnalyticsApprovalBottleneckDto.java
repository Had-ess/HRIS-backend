package com.hris.analytics.dto;

import java.util.UUID;

public record AnalyticsApprovalBottleneckDto(
    UUID approverUserId,
    String approverName,
    long pendingCount,
    double averageDecisionHours,
    long rejectedCount
) {
}
