package com.hris.auth.dto;

import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeCreateDto(
    @NotNull UUID userId,
    @NotBlank String employeeCode,
    @NotNull LocalDate hireDate,
    @NotBlank String jobTitle,
    @NotNull EmployeeStatus status,
    @NotNull ContractType contractType,
    @NotNull UUID departmentId,
    @NotNull UUID workScheduleId
) {}
