package com.hris.settings.calendar.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HrCalendarMutationDto(
    @NotBlank @Size(max = 80) String code,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 80) String country,
    @NotBlank @Size(max = 80) String timezone,
    @NotNull @Min(1) @Max(24) Integer hoursPerDay,
    @NotBlank @Size(max = 50) String source,
    @NotNull Boolean active
) {
}
