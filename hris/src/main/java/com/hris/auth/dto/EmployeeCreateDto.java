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
    @NotEmpty List<UUID> profileIds,
    @NotBlank String employeeCode,
    @NotNull LocalDate hireDate,
    @NotBlank String jobTitle,
    @NotNull EmployeeStatus status,
    @NotNull ContractType contractType,
    @NotNull UUID departmentId,
    UUID supervisorEmployeeId,
    @NotNull UUID workScheduleId
) {
    public EmployeeCreateDto(
            String username,
            String email,
            String firstName,
            String lastName,
            String password,
            Boolean temporaryPassword,
            List<UUID> profileIds,
            String employeeCode,
            LocalDate hireDate,
            String jobTitle,
            EmployeeStatus status,
            ContractType contractType,
            UUID departmentId,
            UUID workScheduleId) {
        this(
            username,
            email,
            firstName,
            lastName,
            password,
            temporaryPassword,
            profileIds,
            employeeCode,
            hireDate,
            jobTitle,
            status,
            contractType,
            departmentId,
            null,
            workScheduleId
        );
    }
}
