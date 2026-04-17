package com.hris.organisation.dto;

import com.hris.organisation.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ProjectAssignmentCreateDto(
    @NotNull UUID employeeId,
    @NotNull UUID supervisorId,
    @NotNull ProjectRole assignmentRole,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {}
