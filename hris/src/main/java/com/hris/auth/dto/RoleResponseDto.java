package com.hris.auth.dto;

import java.util.UUID;

public record RoleResponseDto(
    UUID id,
    String code,
    String name,
    boolean systemRole,
    boolean active,
    UUID parentId,
    String parentName,
    long assignedUsersCount
) {
}
