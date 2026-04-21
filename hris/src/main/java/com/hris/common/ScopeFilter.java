package com.hris.common;

import com.hris.analytics.enums.ScopeType;

import java.util.List;
import java.util.UUID;

public record ScopeFilter(
    ScopeType type,
    UUID entityId,
    List<UUID> entityIds
) {}
