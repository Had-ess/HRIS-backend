package com.hris.organisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProjectTeamCreateDto(
    @NotBlank String name,
    @NotNull UUID departmentId,
    @NotNull UUID supervisorEmployeeId,
    @NotNull List<UUID> employeeIds,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {}
