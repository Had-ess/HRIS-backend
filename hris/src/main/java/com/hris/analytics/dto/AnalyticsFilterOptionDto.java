package com.hris.analytics.dto;

import java.util.UUID;

public record AnalyticsFilterOptionDto(
    UUID id,
    String code,
    String label
) {
}
