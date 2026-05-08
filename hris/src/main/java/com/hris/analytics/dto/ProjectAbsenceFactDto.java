package com.hris.analytics.dto;

import com.hris.analytics.enums.RiskLevel;

import java.util.UUID;

public record ProjectAbsenceFactDto(
    UUID projectId,
    String projectName,
    UUID teamId,
    int absentEmployees,
    int absenceDays,
    int affectedMembers,
    int estimatedDelayDays,
    RiskLevel riskLevel
) {
}
