package com.hris.access.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record MenuAssignmentUpdateDto(@NotEmpty List<UUID> menuItemIds) {
}
