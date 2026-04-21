package com.hris.admin.controller;

import com.hris.admin.dto.AdminRequestResponseDto;
import com.hris.admin.dto.AdminRequestRejectDto;
import com.hris.admin.dto.CreateAdminRequestDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.mapper.AdminRequestMapper;
import com.hris.admin.service.AdminRequestService;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    private final AdminRequestMapper adminRequestMapper;
    private final PermissionAuthorizationService permissionAuthorizationService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> create(
            @Valid @RequestBody CreateAdminRequestDto dto, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        AdminRequest request = adminRequestService.create(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(toDto(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminRequestResponseDto>>> getMyRequests(
            Pageable pageable, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        Page<AdminRequest> page = adminRequestService.getMyRequests(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(page.map(this::toDto))));
    }

    @GetMapping("/incoming")
    public ResponseEntity<ApiResponse<PageResponse<AdminRequestResponseDto>>> getIncoming(
            Pageable pageable,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ADMIN_REQUEST", "PROCESS", "HR_ADMIN", "ADMINISTRATION");
        Page<AdminRequest> page = adminRequestService.getIncoming(pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(page.map(this::toDto))));
    }

    @PatchMapping("/{id}/process")
    public ResponseEntity<ApiResponse<Void>> process(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "ADMIN_REQUEST", "PROCESS", "HR_ADMIN", "ADMINISTRATION");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        adminRequestService.process(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/in-progress")
    public ResponseEntity<ApiResponse<Void>> markInProgress(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "ADMIN_REQUEST", "PROCESS", "HR_ADMIN", "ADMINISTRATION");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        adminRequestService.markInProgress(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminRequestRejectDto dto,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, "ADMIN_REQUEST", "REJECT", "HR_ADMIN", "ADMINISTRATION");
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

    private AdminRequestResponseDto toDto(AdminRequest request) {
        AdminRequestResponseDto dto = adminRequestMapper.toDto(request);
        return new AdminRequestResponseDto(
            dto.id(),
            dto.requesterId(),
            resolveUserName(dto.requesterId()),
            dto.requestTypeId(),
            dto.trackingNumber(),
            dto.description(),
            dto.urgencyLevel(),
            dto.status(),
            dto.metadata(),
            dto.rejectionReason(),
            dto.submittedAt(),
            dto.resolvedAt(),
            dto.resolvedById()
        );
    }

    private String resolveUserName(UUID userId) {
        return userRepository.findById(userId)
            .map(this::toDisplayName)
            .orElse(null);
    }

    private String toDisplayName(User user) {
        String fullName = (user.getFirstName() + " " + user.getLastName()).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }
}
