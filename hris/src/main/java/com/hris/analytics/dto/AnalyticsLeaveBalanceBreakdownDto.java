package com.hris.analytics.dto;

import java.util.UUID;

public record AnalyticsLeaveBalanceBreakdownDto(
    UUID id,
    String code,
    String label,
    long availableDays,
    long reservedDays,
    long usedDays,
    long acquiredDays,
    long balanceCount
) {
}
