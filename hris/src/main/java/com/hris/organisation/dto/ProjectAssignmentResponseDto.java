package com.hris.organisation.dto;

import com.hris.organisation.enums.ProjectRole;

import java.time.LocalDate;
import java.util.UUID;

public record ProjectAssignmentResponseDto(
    UUID id,
    UUID employeeId,
    UUID projectId,
    UUID supervisorId,
    ProjectRole assignmentRole,
    LocalDate startDate,
    LocalDate endDate,
    boolean isActive
) {}
