package com.hris.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateLocaleDto(
    @NotBlank String locale
) {}
