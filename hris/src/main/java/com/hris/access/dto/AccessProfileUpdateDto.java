package com.hris.access.dto;

import jakarta.validation.constraints.Size;

public record AccessProfileUpdateDto(
    @Size(max = 80) String code,
    @Size(max = 150) String displayKey,
    @Size(max = 150) String descriptionKey,
    Boolean active
) {
}
