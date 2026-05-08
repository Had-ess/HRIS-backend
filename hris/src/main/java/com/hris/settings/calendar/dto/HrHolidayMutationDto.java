package com.hris.settings.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HrHolidayMutationDto(
    @NotNull LocalDate date,
    @NotBlank @Size(max = 255) String name,
    Boolean recurring
) {
}
