package com.hris.auth.dto;

import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmployeeCreateDto(
    @NotBlank String username,
    @NotBlank @Email String email,
    @NotBlank @Size(max = 255) String firstName,
    @NotBlank @Size(max = 255) String lastName,
    @NotBlank String password,
    Boolean temporaryPassword,
    @NotEmpty List<UUID> roleIds,
    @NotBlank String employeeCode,
    @NotNull LocalDate hireDate,
    @NotBlank String jobTitle,
    @NotNull EmployeeStatus status,
    @NotNull ContractType contractType,
    @NotNull UUID departmentId,
    @NotNull UUID workScheduleId
) {}
