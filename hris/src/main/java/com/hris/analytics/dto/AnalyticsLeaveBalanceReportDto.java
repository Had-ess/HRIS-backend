package com.hris.analytics.dto;

import java.util.List;

public record AnalyticsLeaveBalanceReportDto(
    long availableDays,
    long reservedDays,
    long usedDays,
    long acquiredDays,
    long negativeBalanceCount,
    long manualAdjustmentsCount,
    List<AnalyticsLeaveBalanceBreakdownDto> byLeaveType,
    List<AnalyticsLeaveBalanceBreakdownDto> byDepartment
) {
}
