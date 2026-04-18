package com.hris.auth.controller;

import com.hris.auth.dto.UserRoleAssignmentDto;
import com.hris.auth.dto.UpdateLocaleDto;
import com.hris.auth.dto.UserResponseDto;
import com.hris.auth.entity.Role;
import com.hris.auth.service.UserRoleAssignmentService;
import com.hris.auth.service.UserService;
import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDto>> getCurrentUser(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(userService.getCurrentUser(userId)));
    }

    @PatchMapping("/me/locale")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateLocale(
            Authentication auth,
            @Valid @RequestBody UpdateLocaleDto dto) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(userService.updateLocale(userId, dto)));
    }

    @GetMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<List<Role>>> getUserRoles(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "USER", "READ", "HR_ADMIN");
        return ResponseEntity.ok(ApiResponse.ok(userRoleAssignmentService.getRoles(id)));
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<List<Role>>> assignRole(
            @PathVariable UUID id,
            @Valid @RequestBody UserRoleAssignmentDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "USER", "ASSIGN_ROLE", "HR_ADMIN");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(userRoleAssignmentService.assignRole(id, dto.roleId(), actorId)));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    public ResponseEntity<ApiResponse<Void>> removeRole(
            @PathVariable UUID id,
            @PathVariable UUID roleId,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "USER", "ASSIGN_ROLE", "HR_ADMIN");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        userRoleAssignmentService.removeRole(id, roleId, actorId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
