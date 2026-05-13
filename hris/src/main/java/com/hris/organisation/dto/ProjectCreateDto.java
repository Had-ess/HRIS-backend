package com.hris.organisation.dto;

import com.hris.organisation.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ProjectCreateDto(
    @NotBlank String name,
    @NotBlank String code,
    @NotNull ProjectStatus status,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {}
