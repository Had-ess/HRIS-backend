package com.hris.auth.controller;

import com.hris.auth.dto.PermissionCreateDto;
import com.hris.auth.dto.PermissionResponseDto;
import com.hris.auth.dto.PermissionUpdateDto;
import com.hris.auth.service.PermissionService;
import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponseDto>>> getAll(Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "PERMISSION", "READ", "ADMINISTRATION");
        return ResponseEntity.ok(ApiResponse.ok(permissionService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponseDto>> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "PERMISSION", "READ", "ADMINISTRATION");
        return ResponseEntity.ok(ApiResponse.ok(permissionService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PermissionResponseDto>> create(
            @Valid @RequestBody PermissionCreateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "PERMISSION", "CREATE", "ADMINISTRATION");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(permissionService.create(dto)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody PermissionUpdateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "PERMISSION", "UPDATE", "ADMINISTRATION");
        return ResponseEntity.ok(ApiResponse.ok(permissionService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "PERMISSION", "DELETE", "ADMINISTRATION");
        permissionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
