package com.hris.leave.controller;

import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.leave.dto.CreateLeaveRequestDto;
import com.hris.leave.dto.FileAttachmentDto;
import com.hris.leave.dto.LeaveRequestPreviewDto;
import com.hris.leave.dto.LeaveRequestPreviewRequestDto;
import com.hris.leave.dto.LeaveRequestResponseDto;
import com.hris.leave.dto.SaveLeaveDraftDto;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.service.AttachmentDownload;
import com.hris.leave.service.LeaveRequestQueryService;
import com.hris.leave.service.LeaveRequestService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final LeaveRequestQueryService leaveRequestQueryService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    // TODO(future): extract authorize()+getCurrentUserId() into an
    // @AuthorizedActor argument resolver to reduce controller boilerplate.
    // See L4 in HRIS_CODE_REVIEW.md for rationale.
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LeaveRequestResponseDto>> create(
            @Valid @RequestBody CreateLeaveRequestDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "LEAVE_REQUEST_CREATE",
            "LEAVE_REQUEST_READ",
            "LEAVE_REQUEST_READ_OWN"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        LeaveRequest request = leaveRequestService.create(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(leaveRequestQueryService.toDto(request, userId)));
    }

    @PostMapping("/drafts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LeaveRequestResponseDto>> createDraft(
            @RequestBody SaveLeaveDraftDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "LEAVE_REQUEST_CREATE",
            "LEAVE_REQUEST_READ_OWN",
            "LEAVE_REQUEST_READ",
            "LEAVE_REQUEST_READ_GLOBAL",
            "LEAVE_REQUEST_MANAGE"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        LeaveRequest draft = leaveRequestService.createDraft(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(leaveRequestQueryService.toDto(draft, userId)));
    }

    @PatchMapping("/{id}/draft")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LeaveRequestResponseDto>> updateDraft(
            @PathVariable UUID id,
            @RequestBody SaveLeaveDraftDto dto,
            Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "LEAVE_REQUEST_CREATE",
            "LEAVE_REQUEST_READ_OWN",
            "LEAVE_REQUEST_READ",
            "LEAVE_REQUEST_READ_GLOBAL",
            "LEAVE_REQUEST_MANAGE"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        LeaveRequest draft = leaveRequestService.updateDraft(id, dto, userId);
        return ResponseEntity.ok(ApiResponse.ok(leaveRequestQueryService.toDto(draft, userId)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LeaveRequestResponseDto>> submitDraft(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "LEAVE_REQUEST_CREATE",
            "LEAVE_REQUEST_READ_OWN",
            "LEAVE_REQUEST_READ",
            "LEAVE_REQUEST_READ_GLOBAL",
            "LEAVE_REQUEST_MANAGE"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        LeaveRequest submitted = leaveRequestService.submitDraft(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(leaveRequestQueryService.toDto(submitted, userId)));
    }

    @DeleteMapping("/{id}/draft")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteDraft(
            @PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "LEAVE_REQUEST_CREATE",
            "LEAVE_REQUEST_READ_OWN",
            "LEAVE_REQUEST_READ",
            "LEAVE_REQUEST_READ_GLOBAL",
            "LEAVE_REQUEST_MANAGE",
            "LEAVE_REQUEST_CANCEL_OWN"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        leaveRequestService.deleteDraft(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LeaveRequestPreviewDto>> preview(
            @Valid @RequestBody LeaveRequestPreviewRequestDto dto,
            Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "LEAVE_REQUEST_CREATE",
            "LEAVE_REQUEST_READ",
            "LEAVE_REQUEST_READ_OWN"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(leaveRequestService.preview(dto, userId)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestResponseDto>>> getMyRequests(
            @RequestParam(required = false) LeaveStatus status,
            Pageable pageable, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "LEAVE_REQUEST_READ_OWN",
            "LEAVE_REQUEST_CREATE",
            "LEAVE_REQUEST_READ",
            "LEAVE_REQUEST_READ_GLOBAL",
            "LEAVE_REQUEST_APPROVE",
            "LEAVE_REQUEST_MANAGE"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        Page<LeaveRequest> page = leaveRequestService.getMyRequests(userId, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
            leaveRequestQueryService.toDtoPage(page, userId)
        )));
    }

    @GetMapping("/visible")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestResponseDto>>> getVisibleRequests(
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) UUID employeeId,
            Pageable pageable,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "LEAVE_REQUEST", "READ");
        UUID requesterId = SecurityUtils.getCurrentUserId(authentication);
        Page<LeaveRequest> page = leaveRequestService.getVisibleRequests(requesterId, status, employeeId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
            leaveRequestQueryService.toDtoPage(page, requesterId)
        )));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LeaveRequestResponseDto>> getById(
            @PathVariable UUID id, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        LeaveRequest request = leaveRequestService.getById(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(leaveRequestQueryService.toDto(request, userId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "LEAVE_REQUEST_CANCEL_OWN",
            "LEAVE_REQUEST_READ_OWN",
            "LEAVE_REQUEST_CREATE",
            "LEAVE_REQUEST_READ"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        leaveRequestService.cancel(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FileAttachmentDto>> uploadAttachment(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        var attachment = leaveRequestService.uploadAttachment(id, file, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(leaveRequestQueryService.toAttachmentDto(attachment)));
    }

    @GetMapping("/{id}/attachments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<FileAttachmentDto>>> getAttachments(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(
            leaveRequestQueryService.toAttachmentDtos(leaveRequestService.getAttachments(id, userId))));
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId,
            Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        AttachmentDownload download = leaveRequestService.downloadAttachment(id, attachmentId, userId);
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(download.fileName()).build().toString()
            )
            .contentType(resolveMediaType(download.mimeType()))
            .body(download.resource());
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId,
            Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        leaveRequestService.deleteAttachment(id, attachmentId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private MediaType resolveMediaType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
