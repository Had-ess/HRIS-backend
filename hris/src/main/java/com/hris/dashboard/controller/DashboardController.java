package com.hris.dashboard.controller;

import com.hris.common.ApiResponse;
import com.hris.dashboard.dto.DirectorDashboardDto;
import com.hris.dashboard.dto.EmployeeDashboardDto;
import com.hris.dashboard.dto.HrDashboardDto;
import com.hris.dashboard.dto.SupervisorDashboardDto;
import com.hris.dashboard.service.DashboardService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<EmployeeDashboardDto>> getMyDashboard(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getEmployeeDashboard(userId)));
    }

    @GetMapping("/supervisor")
    public ResponseEntity<ApiResponse<SupervisorDashboardDto>> getSupervisorDashboard(Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "APPROVAL_STEP_READ",
            "ANALYTICS_READ_SCOPED",
            "DASHBOARD_SUPERVISOR_VIEW"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getSupervisorDashboard(userId)));
    }

    @GetMapping("/hr")
    public ResponseEntity<ApiResponse<HrDashboardDto>> getHrDashboard(Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "EMPLOYEE_MANAGE",
            "ADMIN_REQUEST_PROCESS",
            "LEAVE_BALANCE_MANAGE",
            "DASHBOARD_HR_VIEW"
        );
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getHrDashboard()));
    }

    @GetMapping("/director")
    public ResponseEntity<ApiResponse<DirectorDashboardDto>> getDirectorDashboard(Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "ANALYTICS_READ_GLOBAL"
        );
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDirectorDashboard()));
    }
}
