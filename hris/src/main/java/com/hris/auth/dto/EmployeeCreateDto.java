package com.hris.auth.dto;

import com.hris.auth.enums.ContractType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Onboarding request. New employees always start ACTIVE — status transitions
 * go through the lifecycle endpoints, never through creation. The contract
 * dates feed the initial employee_contracts row created at onboarding.
 */
public record EmployeeCreateDto(
    @NotBlank @Size(max = 255) String username,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 255) String firstName,
    @NotBlank @Size(max = 255) String lastName,
    @NotEmpty List<UUID> profileIds,
    @NotBlank @Size(max = 50, message = "Employee code must be at most 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Employee code may only contain letters, digits, _ and -")
    String employeeCode,
    @NotNull LocalDate hireDate,
    @NotNull UUID jobTitleId,
    @NotNull ContractType contractType,
    LocalDate contractEndDate,
    LocalDate probationEndDate,
    @NotNull UUID departmentId,
    UUID supervisorEmployeeId,
    @NotNull UUID workScheduleId,
    String location,
    @Pattern(regexp = "^[01][0-9]{7}$", message = "CIN must be 8 digits starting with 0 or 1") String cin,
    /**
     * Optional recruitment new-hire handoff this employee is being created from.
     * When present, onboarding completes the handoff (links the employee, fills the
     * requisition headcount) in the same transaction.
     */
    UUID newHireId
) {
    public EmployeeCreateDto(
            String username,
            String email,
            String firstName,
            String lastName,
            List<UUID> profileIds,
            String employeeCode,
            LocalDate hireDate,
            UUID jobTitleId,
            ContractType contractType,
            UUID departmentId,
            UUID workScheduleId) {
        this(
            username,
            email,
            firstName,
            lastName,
            profileIds,
            employeeCode,
            hireDate,
            jobTitleId,
            contractType,
            null,
            null,
            departmentId,
            null,
            workScheduleId,
            null,
            null,
            null
        );
    }
}
