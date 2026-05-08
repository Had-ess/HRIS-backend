package com.hris.settings.quick.controller;

import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import com.hris.settings.quick.dto.QuickSettingsDto;
import com.hris.settings.quick.dto.QuickSettingsMutationDto;
import com.hris.settings.quick.service.QuickSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/quick")
@RequiredArgsConstructor
public class QuickSettingsController {

    private final QuickSettingsService quickSettingsService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<QuickSettingsDto>> get(Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "HR_CALENDAR", "READ");
        return ResponseEntity.ok(ApiResponse.ok(quickSettingsService.get()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<QuickSettingsDto>> update(
            @Valid @RequestBody QuickSettingsMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "SETTINGS", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            quickSettingsService.update(dto, SecurityUtils.getCurrentUserId(authentication))
        ));
    }
}
