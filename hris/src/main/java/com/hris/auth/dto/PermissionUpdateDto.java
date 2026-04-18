package com.hris.auth.dto;

import jakarta.validation.constraints.Size;

public record PermissionUpdateDto(
    @Size(max = 100, message = "Permission name must be at most 100 characters")
    String name,

    @Size(max = 100, message = "Permission resource must be at most 100 characters")
    String resource,

    @Size(max = 50, message = "Permission action must be at most 50 characters")
    String action,

    @Size(max = 500, message = "Permission description must be at most 500 characters")
    String description,

    Boolean active
) {
}
