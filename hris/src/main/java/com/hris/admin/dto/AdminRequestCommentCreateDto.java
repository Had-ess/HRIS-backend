package com.hris.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminRequestCommentCreateDto(
    @NotBlank String comment,
    Boolean internal
) {
}
