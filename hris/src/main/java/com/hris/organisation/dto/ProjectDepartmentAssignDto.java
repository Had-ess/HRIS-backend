package com.hris.organisation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProjectDepartmentAssignDto(
    @NotNull UUID departmentId,
    Boolean isLead
) {
}
