package com.hris.access.controller;

import com.hris.access.dto.AccessProfileAssignmentDto;
import com.hris.access.dto.UserProfileSummaryDto;
import com.hris.access.service.UserAccessAssignmentService;
import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAccessAssignmentController {

    private final UserAccessAssignmentService userAccessAssignmentService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/{id}/profiles")
    public ResponseEntity<ApiResponse<List<UserProfileSummaryDto>>> getProfiles(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "USER", "READ");
        return ResponseEntity.ok(ApiResponse.ok(userAccessAssignmentService.getProfiles(id)));
    }

    @PostMapping("/{id}/profiles")
    public ResponseEntity<ApiResponse<List<UserProfileSummaryDto>>> assignProfile(
            @PathVariable UUID id,
            @Valid @RequestBody AccessProfileAssignmentDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "USER", "ASSIGN_PROFILE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            userAccessAssignmentService.assignProfile(id, dto.profileId(), actorId)));
    }

    @DeleteMapping("/{id}/profiles/{profileId}")
    public ResponseEntity<ApiResponse<Void>> removeProfile(
            @PathVariable UUID id,
            @PathVariable UUID profileId,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "USER", "ASSIGN_PROFILE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        userAccessAssignmentService.removeProfile(id, profileId, actorId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
