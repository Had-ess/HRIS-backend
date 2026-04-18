package com.hris.auth.dto;

import java.util.UUID;

public record PermissionResponseDto(
    UUID id,
    String name,
    String resource,
    String action,
    String description,
    boolean active
) {
}
