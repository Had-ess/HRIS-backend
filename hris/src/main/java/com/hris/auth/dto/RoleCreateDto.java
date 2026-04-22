package com.hris.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RoleCreateDto(
    @NotBlank(message = "code must not be blank")
    @Size(max = 50, message = "code must be at most 50 characters")
    String code,

    @NotBlank(message = "name must not be blank")
    @Size(max = 255, message = "name must be at most 255 characters")
    String name,

    Boolean active,
    UUID parentId
) {
}
