package com.hris.document.controller;

import com.hris.common.ApiResponse;
import com.hris.document.dto.DocumentDtos.DocumentDto;
import com.hris.document.dto.DocumentDtos.UploadMetadata;
import com.hris.document.enums.DocumentType;
import com.hris.document.service.EmployeeDocumentService;
import com.hris.document.service.EmployeeDocumentService.DocumentDownload;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentController {

    private final EmployeeDocumentService documentService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/documents/my")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> myDocuments(Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "DOCUMENT", "MANAGE_OWN");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(documentService.listOwn(userId)));
    }

    @PostMapping(value = "/documents/my", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentDto>> uploadOwn(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") DocumentType docType,
            @RequestParam("title") String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(required = false) String note) {
        permissionAuthorizationService.authorize(authentication, "DOCUMENT", "MANAGE_OWN");
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        UploadMetadata metadata = new UploadMetadata(docType, title, issueDate, expiryDate, note);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(documentService.uploadOwn(userId, file, metadata)));
    }

    @GetMapping("/employees/{employeeId}/documents")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> listForEmployee(
            @PathVariable UUID employeeId, Authentication authentication) {
        UUID requesterId = SecurityUtils.getCurrentUserId(authentication);
        // own vault is reachable through this path too; anyone else needs DOCUMENT_READ
        if (!permissionAuthorizationService.hasPermission(authentication, "DOCUMENT", "READ")) {
            permissionAuthorizationService.authorize(authentication, "DOCUMENT", "MANAGE_OWN");
        }
        return ResponseEntity.ok(ApiResponse.ok(documentService.listFor(employeeId, requesterId)));
    }

    @PostMapping(value = "/employees/{employeeId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentDto>> uploadForEmployee(
            @PathVariable UUID employeeId,
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") DocumentType docType,
            @RequestParam("title") String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(required = false) String note) {
        permissionAuthorizationService.authorize(authentication, "DOCUMENT", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        UploadMetadata metadata = new UploadMetadata(docType, title, issueDate, expiryDate, note);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(documentService.uploadFor(employeeId, file, metadata, actorId)));
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable UUID documentId, Authentication authentication) {
        UUID requesterId = SecurityUtils.getCurrentUserId(authentication);
        boolean hasScopedRead = permissionAuthorizationService
            .hasPermission(authentication, "DOCUMENT", "READ");
        DocumentDownload download = documentService.download(documentId, requesterId, hasScopedRead);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + download.fileName() + "\"")
            .contentType(MediaType.parseMediaType(download.mimeType()))
            .body(download.resource());
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID documentId, Authentication authentication) {
        UUID requesterId = SecurityUtils.getCurrentUserId(authentication);
        boolean hasScopedManage = permissionAuthorizationService
            .hasPermission(authentication, "DOCUMENT", "MANAGE");
        documentService.delete(documentId, requesterId, hasScopedManage);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
