package com.hris.leave.controller;

import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.leave.dto.CreateLeaveRequestDto;
import com.hris.leave.dto.FileAttachmentDto;
import com.hris.leave.dto.LeaveRequestResponseDto;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.service.AttachmentDownload;
import com.hris.leave.service.LeaveRequestQueryService;
import com.hris.leave.service.LeaveRequestService;
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

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LeaveRequestResponseDto>> create(
            @Valid @RequestBody CreateLeaveRequestDto dto, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        LeaveRequest request = leaveRequestService.create(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(leaveRequestQueryService.toDto(request, userId)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestResponseDto>>> getMyRequests(
            @RequestParam(required = false) LeaveStatus status,
            Pageable pageable, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        Page<LeaveRequest> page = leaveRequestService.getMyRequests(userId, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
            leaveRequestQueryService.toDtoPage(page, userId)
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
