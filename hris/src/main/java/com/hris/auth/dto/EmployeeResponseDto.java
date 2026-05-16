package com.hris.auth.dto;

import com.hris.auth.enums.AccountStatus;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeResponseDto(
    UUID id,
    UUID userId,
    String employeeCode,
    LocalDate hireDate,
    String jobTitle,
    EmployeeStatus status,
    ContractType contractType,
    UUID departmentId,
    UUID supervisorEmployeeId,
    UUID workScheduleId,
    UserResponseDto user,
    AccountStatus accountStatus
) {
    public EmployeeResponseDto(
            UUID id,
            UUID userId,
            String employeeCode,
            LocalDate hireDate,
            String jobTitle,
            EmployeeStatus status,
            ContractType contractType,
            UUID departmentId,
            UUID workScheduleId,
            UserResponseDto user) {
        this(
            id,
            userId,
            employeeCode,
            hireDate,
            jobTitle,
            status,
            contractType,
            departmentId,
            null,
            workScheduleId,
            user,
            null
        );
    }
}
