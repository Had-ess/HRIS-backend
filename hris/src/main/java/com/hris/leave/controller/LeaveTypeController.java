package com.hris.leave.controller;

import com.hris.common.ApiResponse;
import com.hris.leave.dto.LeaveTypeCreateDto;
import com.hris.leave.dto.LeaveTypeDto;
import com.hris.leave.dto.LeaveTypeUpdateDto;
import com.hris.leave.acquisition.dto.LeaveAcquisitionPolicyDto;
import com.hris.leave.service.LeaveTypeService;
import com.hris.leave.service.LeaveAcquisitionPolicyService;
import com.hris.security.PermissionAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave-types")
@RequiredArgsConstructor
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;
    private final LeaveAcquisitionPolicyService leaveAcquisitionPolicyService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaveTypeDto>>> getAll(Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "LEAVE_TYPE", "READ");
        return ResponseEntity.ok(ApiResponse.ok(leaveTypeService.getAllActive()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LeaveTypeDto>> create(
            @Valid @RequestBody LeaveTypeCreateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "LEAVE_TYPE", "MANAGE");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(leaveTypeService.create(dto)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<LeaveTypeDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody LeaveTypeUpdateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "LEAVE_TYPE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(leaveTypeService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "LEAVE_TYPE", "MANAGE");
        leaveTypeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{leaveTypeId}/acquisition-policies")
    public ResponseEntity<ApiResponse<List<LeaveAcquisitionPolicyDto>>> getAcquisitionPolicies(
            @PathVariable UUID leaveTypeId,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "ACQUISITION_POLICY_READ",
            "ACQUISITION_POLICY_MANAGE"
        );
        return ResponseEntity.ok(ApiResponse.ok(leaveAcquisitionPolicyService.getByLeaveTypeId(leaveTypeId)));
    }
}
