package com.hris.organisation.dto;

import java.util.UUID;

public record ProjectDepartmentResponseDto(
    UUID id,
    UUID departmentId,
    String name,
    String code,
    boolean isActive,
    boolean isLead
) {
}
