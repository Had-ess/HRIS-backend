package com.hris.admin.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
public record CreateAdminRequestDto(
    @NotNull UUID requestTypeId,
    @NotBlank @Size(max = 255) String subject,
    @NotBlank @Size(max = 4000) String description
) {}
