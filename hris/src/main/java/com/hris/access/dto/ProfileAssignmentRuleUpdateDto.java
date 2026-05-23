package com.hris.access.dto;

import com.hris.access.enums.RuleAction;
import com.hris.access.enums.ScopeStrategy;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public record ProfileAssignmentRuleUpdateDto(
    UUID profileId,
    RuleAction action,
    ScopeStrategy scopeStrategy,
    @Min(1) Integer priority,
    Boolean active,
    String description
) {
}
