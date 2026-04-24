package com.hris.organisation.dto;

import com.hris.organisation.enums.ProjectRole;

import java.time.LocalDate;
import java.util.UUID;

public record ProjectAssignmentViewDto(
    UUID id,
    UUID employeeId,
    UUID employeeUserId,
    String employeeCode,
    String employeeName,
    UUID projectId,
    UUID supervisorId,
    UUID supervisorUserId,
    String supervisorCode,
    String supervisorName,
    ProjectRole assignmentRole,
    LocalDate startDate,
    LocalDate endDate,
    boolean isActive
) {}
