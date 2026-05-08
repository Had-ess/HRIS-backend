package com.hris.access.dto;

import java.util.UUID;

public record AccessProfileResponseDto(
    UUID id,
    String code,
    String displayKey,
    String descriptionKey,
    boolean systemProfile,
    boolean active,
    long assignedUsersCount
) {
}
