package com.hris.leave.controller;

import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.common.event.ActorType;
import com.hris.leave.acquisition.dto.LeaveAcquisitionPolicyDto;
import com.hris.leave.dto.LeaveAccrualRunDto;
import com.hris.leave.service.LeaveAccrualService;
import com.hris.leave.service.LeaveAcquisitionPolicyService;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accrual-runs")
@RequiredArgsConstructor
public class LeaveAccrualRunController {

    private final LeaveAccrualService leaveAccrualService;
    private final LeaveAcquisitionPolicyService leaveAcquisitionPolicyService;

    @GetMapping
    @PreAuthorize("@permissionAuthorizationService.hasAnyPermissionName(authentication, 'ACCRUAL_RUN_READ', 'ACCRUAL_RUN_MANAGE')")
    public ResponseEntity<ApiResponse<PageResponse<LeaveAccrualRunDto>>> getRunHistory(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(leaveAccrualService.getRunHistory(pageable))));
    }

    @GetMapping("/due-policies")
    @PreAuthorize("@permissionAuthorizationService.hasAnyPermissionName(authentication, 'ACCRUAL_RUN_READ', 'ACCRUAL_RUN_MANAGE')")
    public ResponseEntity<ApiResponse<List<LeaveAcquisitionPolicyDto>>> getDuePolicies() {
        List<LeaveAcquisitionPolicyDto> policies = leaveAccrualService.findDuePolicies(LocalDate.now()).stream()
            .map(leaveAcquisitionPolicyService::toDtoView)
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(policies));
    }

    @PostMapping("/run-due")
    @PreAuthorize("@permissionAuthorizationService.hasPermissionName(authentication, 'ACCRUAL_RUN_MANAGE')")
    public ResponseEntity<ApiResponse<LeaveAccrualRunDto>> runDuePolicies(Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        LeaveAccrualRunDto result = leaveAccrualService.runDuePoliciesWithTracking(
            LocalDate.now(), userId, ActorType.USER);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
