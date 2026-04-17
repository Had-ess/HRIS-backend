package com.hris.auth.dto;

import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeUpdateDto(
    String employeeCode,
    LocalDate hireDate,
    String jobTitle,
    EmployeeStatus status,
    ContractType contractType,
    UUID departmentId,
    UUID workScheduleId
) {}
