package com.hris.leave.controller;

import com.hris.common.event.ActorType;
import com.hris.leave.acquisition.entity.LeaveAcquisitionPolicy;
import com.hris.leave.dto.LeaveAccrualRunDto;
import com.hris.leave.service.LeaveAccrualService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accrual-runs")
@RequiredArgsConstructor
public class LeaveAccrualRunController {

    private final LeaveAccrualService leaveAccrualService;

    @GetMapping
    @PreAuthorize("@permissionAuthorizationService.hasAnyPermissionName(authentication, 'ACCRUAL_RUN_READ', 'ACCRUAL_RUN_MANAGE')")
    public Page<LeaveAccrualRunDto> getRunHistory(Pageable pageable) {
        return leaveAccrualService.getRunHistory(pageable);
    }

    @GetMapping("/due-policies")
    @PreAuthorize("@permissionAuthorizationService.hasAnyPermissionName(authentication, 'ACCRUAL_RUN_READ', 'ACCRUAL_RUN_MANAGE')")
    public List<LeaveAcquisitionPolicy> getDuePolicies() {
        return leaveAccrualService.findDuePolicies(LocalDate.now());
    }

    @PostMapping("/run-due")
    @PreAuthorize("@permissionAuthorizationService.hasPermissionName(authentication, 'ACCRUAL_RUN_MANAGE')")
    public ResponseEntity<LeaveAccrualRunDto> runDuePolicies(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        LeaveAccrualRunDto result = leaveAccrualService.runDuePoliciesWithTracking(
            LocalDate.now(), userId, ActorType.USER);
        return ResponseEntity.ok(result);
    }
}
