package com.hris.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccessProfileCreateDto(
    @NotBlank @Size(max = 80) String code,
    @NotBlank @Size(max = 150) String displayKey,
    @Size(max = 150) String descriptionKey,
    Boolean active
) {
}
