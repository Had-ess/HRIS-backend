package com.hris.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminRequestRejectDto(
    @NotBlank(message = "reason must not be blank")
    String reason
) {
}
