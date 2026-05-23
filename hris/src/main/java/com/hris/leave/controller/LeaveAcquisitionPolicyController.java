package com.hris.leave.controller;

import com.hris.common.ApiResponse;
import com.hris.leave.acquisition.dto.LeaveAcquisitionPolicyDto;
import com.hris.leave.acquisition.dto.LeaveAcquisitionPolicyMutationDto;
import com.hris.leave.service.LeaveAcquisitionPolicyService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave-acquisition-policies")
@RequiredArgsConstructor
public class LeaveAcquisitionPolicyController {

    private final LeaveAcquisitionPolicyService service;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaveAcquisitionPolicyDto>>> getAll(Authentication authentication) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LeaveAcquisitionPolicyDto>> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LeaveAcquisitionPolicyDto>> create(
            @Valid @RequestBody LeaveAcquisitionPolicyMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACQUISITION_POLICY", "MANAGE");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(service.create(dto, SecurityUtils.getCurrentUserId(authentication))));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<LeaveAcquisitionPolicyDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody LeaveAcquisitionPolicyMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACQUISITION_POLICY", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, dto, SecurityUtils.getCurrentUserId(authentication))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACQUISITION_POLICY", "MANAGE");
        service.deactivate(id, SecurityUtils.getCurrentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard-delete")
    public ResponseEntity<Void> hardDelete(@PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "ACQUISITION_POLICY", "MANAGE");
        service.hardDelete(id, SecurityUtils.getCurrentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    private void authorizeRead(Authentication authentication) {
        if (permissionAuthorizationService.hasPermission(authentication, "ACQUISITION_POLICY", "READ")
            || permissionAuthorizationService.hasPermission(authentication, "ACQUISITION_POLICY", "MANAGE")) {
            return;
        }
        permissionAuthorizationService.authorize(authentication, "ACQUISITION_POLICY", "READ");
    }
}
