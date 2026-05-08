package com.hris.organisation.dto;

import java.util.UUID;

public record TeamDto(
    UUID id,
    String code,
    String name,
    UUID departmentId,
    String departmentName,
    String departmentCode,
    UUID supervisorEmployeeId,
    String supervisorEmployeeCode,
    String supervisorName,
    boolean active
) {
}
