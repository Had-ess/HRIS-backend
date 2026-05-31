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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<EmployeeDashboardDto>> getMyDashboard(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getEmployeeDashboard(userId, from, to)));
    }

    @GetMapping("/supervisor")
    public ResponseEntity<ApiResponse<SupervisorDashboardDto>> getSupervisorDashboard(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "DASHBOARD_SUPERVISOR_VIEW"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getSupervisorDashboard(userId, from, to)));
    }

    @GetMapping("/hr")
    public ResponseEntity<ApiResponse<HrDashboardDto>> getHrDashboard(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "DASHBOARD_HR_VIEW"
        );
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getHrDashboard(from, to)));
    }

    @GetMapping("/director")
    public ResponseEntity<ApiResponse<DirectorDashboardDto>> getDirectorDashboard(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "DASHBOARD_DIRECTOR_VIEW"
        );
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDirectorDashboard(from, to)));
    }
}
