package com.hris.organisation.dto;

import java.util.UUID;

public record TeamUpdateDto(
    String code,
    String name,
    UUID departmentId,
    UUID projectId,
    UUID supervisorEmployeeId,
    Boolean active
) {
}
