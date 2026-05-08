package com.hris.settings.validation.dto;

import com.hris.access.dto.AccessProfileResponseDto;
import com.hris.auth.dto.PermissionResponseDto;

import java.util.List;

public record ValidationWorkflowOptionsDto(
    List<String> usages,
    List<String> validatorSources,
    List<String> validationModes,
    List<String> fallbackModes,
    List<AccessProfileResponseDto> fallbackProfiles,
    List<PermissionResponseDto> fallbackPermissions
) {
}
