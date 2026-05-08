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
    UUID parentSupervisorEmployeeId,
    @NotNull List<UUID> employeeIds,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {
    public ProjectTeamCreateDto(
            String name,
            UUID departmentId,
            UUID supervisorEmployeeId,
            List<UUID> employeeIds,
            LocalDate startDate,
            LocalDate endDate) {
        this(name, departmentId, supervisorEmployeeId, null, employeeIds, startDate, endDate);
    }
}
