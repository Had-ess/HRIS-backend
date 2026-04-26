package com.hris.analytics.dto;

public record LeaveTypeDistributionDto(
    String leaveTypeCode,
    String leaveTypeName,
    long requestCount,
    long totalDays
) {
}
