package com.hris.analytics.dto;

import com.hris.analytics.enums.AnalyticsScopeType;

import java.util.UUID;

public record AnalyticsScopeOptionDto(
    AnalyticsScopeType scopeType,
    UUID scopeId,
    String label
) {
}
