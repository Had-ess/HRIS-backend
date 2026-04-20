package com.hris.analytics.dto;

import java.util.UUID;

public record AbsenceImpactDto(
    UUID projectId,
    String projectName,
    int totalAbsenceDays,
    int affectedEmployeesCount,
    int estimatedDelayDays,
    com.hris.analytics.enums.RiskLevel riskLevel
) {}
