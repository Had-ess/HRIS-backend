package com.hris.admin.controller;

import com.hris.admin.dto.AdminRequestTypeCreateDto;
import com.hris.admin.dto.AdminRequestTypeDto;
import com.hris.admin.dto.AdminRequestTypeUpdateDto;
import com.hris.admin.service.AdminRequestTypeService;
import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin-request-types")
@RequiredArgsConstructor
public class AdminRequestTypeController {

    private final AdminRequestTypeService adminRequestTypeService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminRequestTypeDto>>> getAll(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(authentication,
            "ADMIN_REQUEST_TYPE_MANAGE",
            "ADMIN_REQUEST_CREATE",
            "ADMIN_REQUEST_INBOX_READ",
            "ADMIN_REQUEST_READ_GLOBAL");
        return ResponseEntity.ok(ApiResponse.ok(adminRequestTypeService.getAll(activeOnly)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminRequestTypeDto>> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(authentication,
            "ADMIN_REQUEST_TYPE_MANAGE",
            "ADMIN_REQUEST_CREATE",
            "ADMIN_REQUEST_INBOX_READ",
            "ADMIN_REQUEST_READ_GLOBAL");
        return ResponseEntity.ok(ApiResponse.ok(adminRequestTypeService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminRequestTypeDto>> create(
            @Valid @RequestBody AdminRequestTypeCreateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ADMIN_REQUEST_TYPE", "MANAGE");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(adminRequestTypeService.create(dto)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminRequestTypeDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRequestTypeUpdateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ADMIN_REQUEST_TYPE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(adminRequestTypeService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ADMIN_REQUEST_TYPE", "MANAGE");
        adminRequestTypeService.deleteOrDeactivate(id);
        return ResponseEntity.noContent().build();
    }
}
