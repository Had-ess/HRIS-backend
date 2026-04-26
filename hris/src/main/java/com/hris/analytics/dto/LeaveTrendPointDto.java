package com.hris.analytics.dto;

public record LeaveTrendPointDto(
    String period,
    long totalRequests,
    long approvedCount,
    long rejectedCount
) {
}
