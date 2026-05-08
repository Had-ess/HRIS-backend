package com.hris.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LeaveTypeUpdateDto(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 255) String name,
    @NotNull Boolean isPaid,
    @NotNull Boolean requiresJustification,
    @NotNull Boolean isActive,
    @NotNull Boolean balanceTracked,
    UUID validationWorkflowId
) {
}
