package com.hris.organisation.dto;

import java.util.UUID;

public record ProjectTeamResponseDto(
    UUID id,
    UUID projectId,
    UUID departmentId,
    String departmentName,
    String departmentCode,
    String name,
    UUID supervisorEmployeeId,
    String supervisorEmployeeCode,
    String supervisorName,
    long memberCount,
    boolean isActive
) {}
