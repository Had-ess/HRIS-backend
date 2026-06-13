package com.hris.auth.dto;

import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeUpdateDto(
    String employeeCode,
    LocalDate hireDate,
    UUID jobTitleId,
    EmployeeStatus status,
    ContractType contractType,
    UUID departmentId,
    UUID supervisorEmployeeId,
    /** PATCH null means keep, so clearing the supervisor needs an explicit flag. */
    Boolean clearSupervisor,
    UUID workScheduleId,
    String location,
    String cin
) {
    public EmployeeUpdateDto(
            String employeeCode,
            LocalDate hireDate,
            UUID jobTitleId,
            EmployeeStatus status,
            ContractType contractType,
            UUID departmentId,
            UUID workScheduleId) {
        this(
            employeeCode,
            hireDate,
            jobTitleId,
            status,
            contractType,
            departmentId,
            null,
            null,
            workScheduleId,
            null,
            null
        );
    }
}
