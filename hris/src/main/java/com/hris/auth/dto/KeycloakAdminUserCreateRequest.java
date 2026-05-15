package com.hris.auth.dto;

public record KeycloakAdminUserCreateRequest(
    String username,
    String email,
    String firstName,
    String lastName
) {
}
