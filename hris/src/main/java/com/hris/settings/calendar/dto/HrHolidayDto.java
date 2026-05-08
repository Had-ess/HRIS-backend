package com.hris.settings.calendar.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HrHolidayDto(
    UUID id,
    UUID calendarId,
    LocalDate date,
    String name,
    boolean recurring,
    Instant createdAt,
    Instant updatedAt
) {
}
