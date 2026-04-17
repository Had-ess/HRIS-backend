package com.hris.auth.dto;

import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.dto.UserResponseDto;

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
    UUID workScheduleId,
    UserResponseDto user
) {}
