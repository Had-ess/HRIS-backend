package com.hris.organisation.dto;

import java.util.UUID;

public record WorkScheduleDto(
    UUID id,
    String name,
    String workingDays,
    int hoursPerDay
) {
}
