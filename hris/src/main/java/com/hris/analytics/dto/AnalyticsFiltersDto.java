package com.hris.analytics.dto;

import java.util.List;

public record AnalyticsFiltersDto(
    List<AnalyticsScopeOptionDto> scopes,
    List<AnalyticsFilterOptionDto> departments,
    List<AnalyticsFilterOptionDto> teams,
    List<AnalyticsFilterOptionDto> leaveTypes,
    List<AnalyticsFilterOptionDto> adminRequestTypes
) {
}
