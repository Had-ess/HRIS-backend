package com.hris.auth.controller;

import com.hris.auth.dto.PermissionResponseDto;
import com.hris.auth.dto.RolePermissionsUpdateDto;
import com.hris.auth.entity.Role;
import com.hris.auth.service.RolePermissionService;
import com.hris.auth.service.RoleService;
import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Role>> create(@RequestBody Role role, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ROLE", "CREATE", "ADMINISTRATION");
        Role saved = roleService.create(role);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(saved));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> update(
            @PathVariable UUID id,
            @RequestBody Role role,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ROLE", "UPDATE", "ADMINISTRATION");
        return ResponseEntity.ok(ApiResponse.ok(roleService.update(id, role)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ROLE", "DELETE", "ADMINISTRATION");
        roleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponseDto>>> getPermissions(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ROLE", "READ", "ADMINISTRATION");
        return ResponseEntity.ok(ApiResponse.ok(rolePermissionService.getPermissions(id)));
    }

    @PostMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponseDto>>> assignPermissions(
            @PathVariable UUID id,
            @Valid @RequestBody RolePermissionsUpdateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ROLE", "ASSIGN_PERMISSION", "ADMINISTRATION");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            rolePermissionService.assignPermissions(id, dto.permissionIds(), actorId)));
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> removePermission(
            @PathVariable UUID id,
            @PathVariable UUID permissionId,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ROLE", "ASSIGN_PERMISSION", "ADMINISTRATION");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        rolePermissionService.removePermission(id, permissionId, actorId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
