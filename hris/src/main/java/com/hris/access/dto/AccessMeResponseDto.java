package com.hris.access.dto;

import java.util.List;

public record AccessMeResponseDto(
    List<String> profileCodes,
    List<AccessPermissionDto> permissions,
    List<String> scopes
) {
}
