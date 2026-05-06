package com.hris.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminRequestTypeUpdateDto(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 255) String name,
    @NotNull Boolean isActive
) {
}
