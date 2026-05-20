package com.hris.organisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TeamCreateDto(
    @NotBlank String code,
    @NotBlank String name,
    @NotNull UUID departmentId,
    @NotNull UUID projectId,
    @NotNull UUID supervisorEmployeeId
) {
}
