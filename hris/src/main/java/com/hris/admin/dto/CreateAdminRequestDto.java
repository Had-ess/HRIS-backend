package com.hris.admin.dto;
import com.hris.leave.enums.UrgencyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record CreateAdminRequestDto(
    @NotNull UUID requestTypeId,
    @NotBlank String description,
    @NotNull UrgencyLevel urgencyLevel,
    String metadata
) {}
