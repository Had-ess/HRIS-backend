package com.hris.organisation.controller;

import com.hris.common.ApiResponse;
import com.hris.organisation.dto.OrgChartDtos.DepartmentNode;
import com.hris.organisation.dto.OrgChartDtos.SpineNode;
import com.hris.organisation.service.OrgChartService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
public class OrgChartController {

    private final OrgChartService orgChartService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/chart")
    public ResponseEntity<ApiResponse<List<DepartmentNode>>> departmentChart(Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "EMPLOYEE_READ",
            "EMPLOYEE_MANAGE"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(orgChartService.getDepartmentChart(userId)));
    }

    @GetMapping("/chart/spine")
    public ResponseEntity<ApiResponse<List<SpineNode>>> spine(Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "EMPLOYEE_READ",
            "EMPLOYEE_MANAGE"
        );
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(orgChartService.getSpine(userId)));
    }
}
