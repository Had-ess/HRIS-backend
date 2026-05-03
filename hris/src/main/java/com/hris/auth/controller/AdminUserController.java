package com.hris.auth.controller;

import com.hris.auth.dto.AdminUserCreateDto;
import com.hris.auth.dto.AdminUserResponseDto;
import com.hris.auth.service.AdminUserService;
import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUserResponseDto>>> getAll(Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "USER", "READ", "ADMINISTRATION");
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.getAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> create(
        @Valid @RequestBody AdminUserCreateDto dto,
        Authentication authentication
    ) {
        permissionAuthorizationService.authorize(authentication, "USER", "CREATE", "ADMINISTRATION");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(adminUserService.create(dto, actorId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        permissionAuthorizationService.authorize(authentication, "USER", "DELETE", "ADMINISTRATION");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        adminUserService.delete(id, actorId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
