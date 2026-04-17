package com.hris.approval.dto;

import jakarta.validation.constraints.NotBlank;

public record ApprovalDecisionDto(
    @NotBlank String comment
) {}
