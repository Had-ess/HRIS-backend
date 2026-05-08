package com.hris.access.dto;

public record AccessPermissionDto(
    String name,
    String resource,
    String action,
    String scope
) {
}
