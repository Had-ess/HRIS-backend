package com.hris.settings.validation.controller;

import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import com.hris.settings.validation.dto.ValidationWorkflowDto;
import com.hris.settings.validation.dto.ValidationWorkflowMutationDto;
import com.hris.settings.validation.dto.ValidationWorkflowOptionsDto;
import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.service.ValidationWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/validation-workflows")
@RequiredArgsConstructor
public class ValidationWorkflowController {

    private final ValidationWorkflowService validationWorkflowService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ValidationWorkflowDto>>> getAll(
            Pageable pageable,
            Authentication authentication) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(validationWorkflowService.getAll(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ValidationWorkflowDto>> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(validationWorkflowService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ValidationWorkflowDto>> create(
            @Valid @RequestBody ValidationWorkflowMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "VALIDATION_WORKFLOW", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(validationWorkflowService.create(dto, actorId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ValidationWorkflowDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ValidationWorkflowMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "VALIDATION_WORKFLOW", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(validationWorkflowService.update(id, dto, actorId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "VALIDATION_WORKFLOW", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        validationWorkflowService.deactivate(id, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<ValidationWorkflowOptionsDto>> getOptions(
            @RequestParam ValidationUsage usage,
            Authentication authentication) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(validationWorkflowService.getOptions(usage)));
    }

    private void authorizeRead(Authentication authentication) {
        if (permissionAuthorizationService.hasPermission(authentication, "VALIDATION_WORKFLOW", "READ")
            || permissionAuthorizationService.hasPermission(authentication, "VALIDATION_WORKFLOW", "MANAGE")) {
            return;
        }
        permissionAuthorizationService.authorize(authentication, "VALIDATION_WORKFLOW", "READ");
    }
}
