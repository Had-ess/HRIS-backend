package com.hris.admin.controller;

import com.hris.admin.dto.AdminRequestResponseDto;
import com.hris.admin.dto.AdminRequestRejectDto;
import com.hris.admin.dto.CreateAdminRequestDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.mapper.AdminRequestMapper;
import com.hris.admin.service.AdminRequestService;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin-requests")
@RequiredArgsConstructor
public class AdminRequestController {

    private final AdminRequestService adminRequestService;
    private final AdminRequestMapper adminRequestMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> create(
            @Valid @RequestBody CreateAdminRequestDto dto, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        AdminRequest request = adminRequestService.create(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(adminRequestMapper.toDto(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminRequestResponseDto>>> getMyRequests(
            Pageable pageable, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        Page<AdminRequest> page = adminRequestService.getMyRequests(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(page.map(adminRequestMapper::toDto))));
    }

    @GetMapping("/incoming")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminRequestResponseDto>>> getIncoming(Pageable pageable) {
        Page<AdminRequest> page = adminRequestService.getIncoming(pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(page.map(adminRequestMapper::toDto))));
    }

    @PatchMapping("/{id}/process")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> process(@PathVariable UUID id, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        adminRequestService.process(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/in-progress")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> markInProgress(@PathVariable UUID id, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        adminRequestService.markInProgress(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminRequestRejectDto dto,
            Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        adminRequestService.reject(id, userId, dto != null ? dto.reason() : null);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        adminRequestService.cancel(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
