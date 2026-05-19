package com.hris.analytics.dto;

import com.hris.analytics.enums.AnalyticsScopeType;
import java.util.List;
import java.util.UUID;

public record AnalyticsOverviewDto(
    AnalyticsScopeType scopeType,
    UUID scopeId,
    String scopeLabel,
    List<AnalyticsOverviewKpiDto> kpis,
    List<AnalyticsOverviewSeriesPointDto> headcountTrend,
    List<AnalyticsLeaveTypeOverviewDto> leaveByType,
    List<AnalyticsOverviewBreakdownDto> approvalBottlenecks,
    List<AnalyticsOverviewBreakdownDto> topLeaveReasons
) {
}
