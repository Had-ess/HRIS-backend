package com.hris.organisation.dto;

import java.util.UUID;

public record TeamUpdateDto(
    String code,
    String name,
    UUID departmentId,
    UUID projectId,
    /** PATCH null means keep, so detaching the project needs an explicit flag. */
    Boolean clearProject,
    UUID supervisorEmployeeId,
    Boolean active
) {
}
