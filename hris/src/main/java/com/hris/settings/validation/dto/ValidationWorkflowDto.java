package com.hris.settings.validation.dto;

import java.time.Instant;
import java.util.UUID;

public record ValidationWorkflowDto(
    UUID id,
    String code,
    String name,
    String usage,
    String validatorSource,
    String validationMode,
    Integer minValidators,
    String fallbackMode,
    UUID fallbackProfileId,
    String fallbackPermissionCode,
    boolean active,
    boolean defaultWorkflow,
    Instant createdAt,
    Instant updatedAt
) {
}
