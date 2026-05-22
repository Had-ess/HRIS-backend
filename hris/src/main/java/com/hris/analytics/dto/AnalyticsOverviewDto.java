package com.hris.analytics.dto;

import java.util.List;

public record AnalyticsOverviewDto(
    List<AnalyticsOverviewKpiDto> kpis,
    List<AnalyticsOverviewSeriesPointDto> headcountTrend,
    List<AnalyticsLeaveTypeOverviewDto> leaveByType,
    List<AnalyticsOverviewBreakdownDto> approvalBottlenecks,
    List<AnalyticsOverviewBreakdownDto> topLeaveReasons
) {
}
