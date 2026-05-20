package com.hris.access.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PermissionAssignmentUpdateDto(@NotNull List<UUID> permissionIds) {
}
