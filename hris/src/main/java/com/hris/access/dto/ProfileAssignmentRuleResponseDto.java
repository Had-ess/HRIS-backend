package com.hris.access.dto;

import com.hris.access.enums.RuleAction;
import com.hris.access.enums.ScopeStrategy;
import com.hris.access.enums.StructuralEventType;

import java.time.Instant;
import java.util.UUID;

public record ProfileAssignmentRuleResponseDto(
    UUID id,
    StructuralEventType triggerEvent,
    UUID profileId,
    String profileCode,
    String profileDisplayKey,
    RuleAction action,
    ScopeStrategy scopeStrategy,
    Integer priority,
    boolean active,
    String description,
    Instant createdAt,
    Instant updatedAt
) {
}
