package com.hris.auth.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RoleUpdateDto(
    @Size(max = 50, message = "code must be at most 50 characters")
    String code,

    @Size(max = 255, message = "name must be at most 255 characters")
    String name,

    Boolean active,
    UUID parentId
) {
}
