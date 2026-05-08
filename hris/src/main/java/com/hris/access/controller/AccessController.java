package com.hris.access.controller;

import com.hris.access.dto.AccessMeResponseDto;
import com.hris.access.dto.NavigationSectionDto;
import com.hris.access.service.AccessResolutionService;
import com.hris.common.ApiResponse;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccessController {

    private final AccessResolutionService accessResolutionService;

    @GetMapping("/access/me")
    public ResponseEntity<ApiResponse<AccessMeResponseDto>> getAccess(Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(accessResolutionService.resolveAccess(userId)));
    }

    @GetMapping("/navigation/me")
    public ResponseEntity<ApiResponse<List<NavigationSectionDto>>> getNavigation(Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(accessResolutionService.resolveNavigation(userId)));
    }
}
