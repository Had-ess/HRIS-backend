package com.hris.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

// GAP-B-16: DepartmentCreateDto for POST
public record DepartmentCreateDto(
    @NotBlank String name,
    @NotBlank String code,
    UUID headEmployeeId,
    UUID parentDepartmentId,
    Boolean isActive
) {}
