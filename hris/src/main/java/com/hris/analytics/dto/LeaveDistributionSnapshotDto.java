package com.hris.analytics.dto;

import java.util.UUID;

public record LeaveDistributionSnapshotDto(
    UUID leaveTypeId,
    String leaveTypeCode,
    String leaveTypeName,
    int requestCount,
    int totalDays
) {
}
