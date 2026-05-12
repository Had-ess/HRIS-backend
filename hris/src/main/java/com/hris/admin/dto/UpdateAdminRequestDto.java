package com.hris.admin.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateAdminRequestDto(
    UUID requestTypeId,
    @Size(max = 255) String subject,
    @Size(max = 4000) String description
) {
}
