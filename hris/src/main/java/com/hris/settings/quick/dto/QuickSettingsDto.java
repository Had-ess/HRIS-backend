package com.hris.settings.quick.dto;

import java.time.Instant;
import java.util.UUID;

public record QuickSettingsDto(
    Integer monthlyAcquisitionRate,
    Integer maxAuthorizationsPerMonth,
    Integer maxAuthorizationHours,
    String workWeekPattern,
    UUID defaultValidationWorkflowId,
    String defaultValidationWorkflowCode,
    String defaultValidationWorkflowName,
    Integer defaultWorkflowSlaHours,
    Integer defaultValidationSlaHours,
    UUID activeCalendarId,
    String activeCalendarCode,
    String activeCalendarName,
    Integer workingHoursPerDay,
    Instant updatedAt
) {
}
