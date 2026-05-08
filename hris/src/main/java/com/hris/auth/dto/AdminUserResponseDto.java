package com.hris.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserResponseDto(
    UUID id,
    String keycloakId,
    String email,
    String firstName,
    String lastName,
    boolean active,
    Instant createdAt,
    Instant lastLogin,
    List<String> profiles
) {
}
