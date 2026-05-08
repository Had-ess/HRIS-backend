package com.hris.settings.quick.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public record QuickSettingsMutationDto(
    @Min(0) Integer monthlyAcquisitionRate,
    @Min(0) Integer maxAuthorizationsPerMonth,
    @Min(0) Integer maxAuthorizationHours,
    String workWeekPattern,
    UUID defaultValidationWorkflowId,
    @Min(1) Integer defaultWorkflowSlaHours,
    @Min(1) Integer defaultValidationSlaHours,
    UUID activeCalendarId,
    @Min(1) @Max(24) Integer workingHoursPerDay
) {
}
