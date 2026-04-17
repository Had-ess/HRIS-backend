package com.hris.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponseDto(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String localePreference,
    boolean isActive,
    Instant createdAt,
    Instant lastLogin
) {}
