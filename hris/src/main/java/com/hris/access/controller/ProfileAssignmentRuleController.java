package com.hris.access.controller;

import com.hris.access.dto.ProfileAssignmentRuleResponseDto;
import com.hris.access.dto.ProfileAssignmentRuleUpdateDto;
import com.hris.access.service.ProfileAssignmentRuleService;
import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile-assignment-rules")
@RequiredArgsConstructor
public class ProfileAssignmentRuleController {

    private static final String PERMISSION_READ = "PROFILE_ASSIGNMENT_RULE_READ";
    private static final String PERMISSION_MANAGE = "PROFILE_ASSIGNMENT_RULE_MANAGE";

    private final ProfileAssignmentRuleService ruleService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProfileAssignmentRuleResponseDto>>> getAll(Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, PERMISSION_READ);
        return ResponseEntity.ok(ApiResponse.ok(ruleService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileAssignmentRuleResponseDto>> getById(
            @PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, PERMISSION_READ);
        return ResponseEntity.ok(ApiResponse.ok(ruleService.getById(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileAssignmentRuleResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProfileAssignmentRuleUpdateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, PERMISSION_MANAGE);
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(ruleService.update(id, dto, actorId)));
    }
}
