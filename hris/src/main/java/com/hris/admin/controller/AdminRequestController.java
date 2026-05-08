package com.hris.admin.controller;

import com.hris.admin.dto.AdminRequestResponseDto;
import com.hris.admin.dto.AdminRequestRejectDto;
import com.hris.admin.dto.CreateAdminRequestDto;
import com.hris.admin.service.AdminRequestQueryService;
import com.hris.admin.service.AdminRequestService;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin-requests")
@RequiredArgsConstructor
public class AdminRequestController {

    private final AdminRequestService adminRequestService;
    private final AdminRequestQueryService adminRequestQueryService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @PostMapping
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> create(
            @Valid @RequestBody CreateAdminRequestDto dto, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        var request = adminRequestService.create(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(adminRequestQueryService.toDto(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminRequestResponseDto>>> getMyRequests(
            Pageable pageable, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        var page = adminRequestService.getMyRequests(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
            adminRequestQueryService.toDtoPage(page))));
    }

    @GetMapping("/incoming")
    public ResponseEntity<ApiResponse<PageResponse<AdminRequestResponseDto>>> getIncoming(
            Pageable pageable,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ADMIN_REQUEST", "PROCESS");
        var page = adminRequestService.getIncoming(pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
            adminRequestQueryService.toDtoPage(page))));
    }

    @PatchMapping("/{id}/process")
    public ResponseEntity<ApiResponse<Void>> process(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "ADMIN_REQUEST", "PROCESS");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        adminRequestService.process(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/in-progress")
    public ResponseEntity<ApiResponse<Void>> markInProgress(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "ADMIN_REQUEST", "PROCESS");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        adminRequestService.markInProgress(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable UUID id,
        @Valid @RequestBody AdminRequestRejectDto dto,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, "ADMIN_REQUEST", "REJECT");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        adminRequestService.reject(id, userId, dto.reason());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        adminRequestService.cancel(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
