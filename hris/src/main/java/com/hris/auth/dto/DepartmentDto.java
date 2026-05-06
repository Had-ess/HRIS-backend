package com.hris.auth.dto;

import java.util.UUID;

// GAP-B-16: DepartmentDto for responses
public record DepartmentDto(
    UUID id,
    String name,
    String code,
    UUID headEmployeeId,
    boolean isActive,
    long employeeCount,
    long projectCount,
    long projectAssignmentCount
) {}
