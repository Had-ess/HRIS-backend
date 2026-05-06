package com.hris.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeaveTypeCreateDto(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 255) String name,
    Boolean isPaid,
    Boolean requiresJustification,
    Boolean isActive
) {
}
