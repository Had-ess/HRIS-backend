package com.hris.analytics.dto;

import com.hris.analytics.enums.ApprovalSourceType;

import java.math.BigDecimal;

public record ApprovalBottleneckSnapshotDto(
    ApprovalSourceType sourceType,
    int approverLevel,
    int pendingCount,
    BigDecimal averageDecisionDays,
    BigDecimal rejectionRate
) {
}
