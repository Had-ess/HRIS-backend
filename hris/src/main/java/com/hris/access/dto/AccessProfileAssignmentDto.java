package com.hris.access.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AccessProfileAssignmentDto(@NotNull UUID profileId) {
}
