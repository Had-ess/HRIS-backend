package com.hris.access.dto;

import java.util.List;
import java.util.UUID;

public record AccessMeResponseDto(
    List<String> profileCodes,
    List<AccessPermissionDto> permissions,
    List<String> scopes,
    List<UUID> scopedDepartmentIds
) {
}
