package com.hris.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminRequestTypeCreateDto(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 4000) String description,
    Boolean requiresAttachment,
    @Positive Integer slaHours,
    Boolean isActive
) {
}
