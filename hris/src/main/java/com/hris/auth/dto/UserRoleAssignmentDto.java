package com.hris.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UserRoleAssignmentDto(
    @NotNull(message = "Role ID is required")
    UUID roleId
) {
}
