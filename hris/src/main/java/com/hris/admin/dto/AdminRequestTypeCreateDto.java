package com.hris.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminRequestTypeCreateDto(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 255) String name,
    Boolean isActive
) {
}
