package com.hris.access.controller;

import com.hris.access.dto.AccessProfileCreateDto;
import com.hris.access.dto.AccessProfileResponseDto;
import com.hris.access.dto.AccessProfileUpdateDto;
import com.hris.access.dto.MenuAssignmentUpdateDto;
import com.hris.access.dto.MenuItemResponseDto;
import com.hris.access.dto.PermissionAssignmentUpdateDto;
import com.hris.access.service.AccessProfileService;
import com.hris.auth.dto.PermissionResponseDto;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/access-profiles")
@RequiredArgsConstructor
public class AccessProfileController {

    private final AccessProfileService accessProfileService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AccessProfileResponseDto>>> getAll(
            Pageable pageable,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACCESS_PROFILE", "READ");
        return ResponseEntity.ok(ApiResponse.ok(accessProfileService.getAll(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccessProfileResponseDto>> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACCESS_PROFILE", "READ");
        return ResponseEntity.ok(ApiResponse.ok(accessProfileService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccessProfileResponseDto>> create(
            @Valid @RequestBody AccessProfileCreateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACCESS_PROFILE", "CREATE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(accessProfileService.create(dto, actorId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AccessProfileResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody AccessProfileUpdateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACCESS_PROFILE", "UPDATE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(accessProfileService.update(id, dto, actorId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACCESS_PROFILE", "DELETE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        accessProfileService.deactivate(id, actorId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard-delete")
    public ResponseEntity<Void> hardDelete(@PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACCESS_PROFILE", "DELETE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        accessProfileService.hardDelete(id, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponseDto>>> getPermissions(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACCESS_PROFILE", "READ");
        return ResponseEntity.ok(ApiResponse.ok(accessProfileService.getPermissions(id)));
    }

    @PostMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponseDto>>> assignPermissions(
            @PathVariable UUID id,
            @Valid @RequestBody PermissionAssignmentUpdateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACCESS_PROFILE", "ASSIGN_PERMISSION");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            accessProfileService.assignPermissions(id, dto.permissionIds(), actorId)));
    }

    @GetMapping("/{id}/menu-items")
    public ResponseEntity<ApiResponse<List<MenuItemResponseDto>>> getMenus(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACCESS_PROFILE", "READ");
        return ResponseEntity.ok(ApiResponse.ok(accessProfileService.getMenus(id)));
    }

    @PostMapping("/{id}/menu-items")
    public ResponseEntity<ApiResponse<List<MenuItemResponseDto>>> assignMenus(
            @PathVariable UUID id,
            @Valid @RequestBody MenuAssignmentUpdateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACCESS_PROFILE", "ASSIGN_MENU");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(accessProfileService.assignMenus(id, dto.menuItemIds(), actorId)));
    }

    @GetMapping("/menu-items")
    public ResponseEntity<ApiResponse<PageResponse<MenuItemResponseDto>>> getMenuItems(
            Pageable pageable,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "MENU_ITEM", "READ");
        return ResponseEntity.ok(ApiResponse.ok(accessProfileService.getAllMenus(pageable)));
    }
}
