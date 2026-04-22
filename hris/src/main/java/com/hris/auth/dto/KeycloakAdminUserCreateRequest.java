package com.hris.auth.dto;

import java.util.List;

public record KeycloakAdminUserCreateRequest(
    String username,
    String email,
    String firstName,
    String lastName,
    String password,
    boolean temporaryPassword,
    List<String> realmRoles
) {
}
