package com.hris.settings.validation.dto;

import com.hris.settings.validation.entity.ValidationFallbackMode;
import com.hris.settings.validation.entity.ValidationMode;
import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.entity.ValidatorSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ValidationWorkflowMutationDto(
    @NotBlank String code,
    @NotBlank String name,
    @NotNull ValidationUsage usage,
    @NotNull ValidatorSource validatorSource,
    @NotNull ValidationMode validationMode,
    Integer minValidators,
    @NotNull ValidationFallbackMode fallbackMode,
    UUID fallbackProfileId,
    String fallbackPermissionCode,
    Boolean active,
    Boolean defaultWorkflow
) {
}
