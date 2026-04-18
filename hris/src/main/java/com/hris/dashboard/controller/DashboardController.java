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
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('DEPT_MANAGER', 'PROJECT_SUPERVISOR', 'HR_ADMIN')")
    public ResponseEntity<ApiResponse<SupervisorDashboardDto>> getSupervisorDashboard(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getSupervisorDashboard(userId)));
    }

    @GetMapping("/hr")
    public ResponseEntity<ApiResponse<HrDashboardDto>> getHrDashboard(Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "DASHBOARD", "HR_VIEW", "HR_ADMIN");
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getHrDashboard()));
    }

    @GetMapping("/director")
    public ResponseEntity<ApiResponse<DirectorDashboardDto>> getDirectorDashboard(Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "DASHBOARD", "DIRECTOR_VIEW", "DIRECTOR", "HR_ADMIN");
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDirectorDashboard()));
    }
}
