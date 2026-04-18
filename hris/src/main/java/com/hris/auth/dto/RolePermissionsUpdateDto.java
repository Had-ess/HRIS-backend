package com.hris.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record RolePermissionsUpdateDto(
    @NotEmpty(message = "At least one permission ID is required")
    List<@NotNull(message = "Permission ID is required") UUID> permissionIds
) {
}
