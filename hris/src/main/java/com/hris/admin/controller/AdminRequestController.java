package com.hris.admin.controller;

import com.hris.admin.dto.*;
import com.hris.admin.enums.AdminRequestStatus;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin-requests")
@RequiredArgsConstructor
public class AdminRequestController {

    private final AdminRequestService adminRequestService;
    private final AdminRequestQueryService adminRequestQueryService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<AdminRequestResponseDto>>> getMyRequests(
            Pageable pageable,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_READ_OWN");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        var page = adminRequestService.getMyRequests(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
            adminRequestQueryService.toDtoPage(page, false))));
    }

    @GetMapping("/my/{id}")
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> getMyRequest(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_READ_OWN");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            adminRequestQueryService.toDto(adminRequestService.getOwnRequest(id, userId), false)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> create(
            @Valid @RequestBody CreateAdminRequestDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_CREATE");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        var request = adminRequestService.create(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(adminRequestQueryService.toDto(request, false)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAdminRequestDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_CREATE");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            adminRequestQueryService.toDto(adminRequestService.update(id, dto, userId), false)));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> submit(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_CREATE");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            adminRequestQueryService.toDto(adminRequestService.submit(id, userId), false)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> cancel(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_CANCEL_OWN");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            adminRequestQueryService.toDto(adminRequestService.cancel(id, userId), false)));
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<ApiResponse<AdminRequestAttachmentDto>> uploadAttachment(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_CREATE");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        adminRequestService.uploadRequesterAttachment(id, file, userId);
        var updatedRequest = adminRequestService.getOwnRequest(id, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
            adminRequestQueryService.toDto(updatedRequest, false).attachments().stream()
                .reduce((first, second) -> second)
                .orElseThrow()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminRequestResponseDto>>> getInbox(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false) AdminRequestStatus status,
            @RequestParam(required = false) String requester,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(required = false) Boolean overdue,
            Pageable pageable,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(authentication,
            "ADMIN_REQUEST_INBOX_READ", "ADMIN_REQUEST_READ_GLOBAL");
        var page = adminRequestService.searchInbox(search, typeId, status, requester, dateFrom, dateTo, overdue, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
            adminRequestQueryService.toDtoPage(page, true))));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminRequestInboxSummaryDto>> getInboxSummary(Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(authentication,
            "ADMIN_REQUEST_INBOX_READ", "ADMIN_REQUEST_READ_GLOBAL");
        return ResponseEntity.ok(ApiResponse.ok(adminRequestService.getInboxSummary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> getInboxRequest(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(authentication,
            "ADMIN_REQUEST_INBOX_READ", "ADMIN_REQUEST_READ_GLOBAL");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        boolean includeInternal = adminRequestService.canViewInternal(userId);
        return ResponseEntity.ok(ApiResponse.ok(
            adminRequestQueryService.toDto(adminRequestService.getInboxRequest(id), includeInternal)));
    }

    @PostMapping("/{id}/start-review")
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> startReview(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_PROCESS");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            adminRequestQueryService.toDto(adminRequestService.startReview(id, userId), true)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> approve(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_APPROVE");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            adminRequestQueryService.toDto(adminRequestService.approve(id, userId), true)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRequestRejectDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_REJECT");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            adminRequestQueryService.toDto(adminRequestService.reject(id, userId, dto), true)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<AdminRequestResponseDto>> complete(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "ADMIN_REQUEST_COMPLETE");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            adminRequestQueryService.toDto(adminRequestService.complete(id, userId), true)));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<AdminRequestCommentDto>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRequestCommentCreateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(authentication,
            "ADMIN_REQUEST_READ_OWN", "ADMIN_REQUEST_INBOX_READ", "ADMIN_REQUEST_READ_GLOBAL");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        adminRequestService.addComment(id, userId, dto);
        var updatedRequest = adminRequestService.getInboxRequest(id);
        boolean includeInternal = adminRequestService.canViewInternal(userId) || updatedRequest.getRequesterUserId().equals(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
            adminRequestQueryService.toDto(updatedRequest, includeInternal).comments().stream()
                .reduce((first, second) -> second)
                .orElseThrow()));
    }

    @PostMapping("/{id}/response-attachments")
    public ResponseEntity<ApiResponse<AdminRequestAttachmentDto>> uploadResponseAttachment(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(authentication,
            "ADMIN_REQUEST_PROCESS", "ADMIN_REQUEST_APPROVE", "ADMIN_REQUEST_COMPLETE");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        adminRequestService.uploadResponseAttachment(id, file, userId);
        var updatedRequest = adminRequestService.getInboxRequest(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
            adminRequestQueryService.toDto(updatedRequest, true).attachments().stream()
                .reduce((first, second) -> second)
                .orElseThrow()));
    }
}
