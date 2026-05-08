package com.hris.settings.calendar.dto;

import java.time.Instant;
import java.util.UUID;

public record HrCalendarDto(
    UUID id,
    String code,
    String name,
    String country,
    String timezone,
    int hoursPerDay,
    String source,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
