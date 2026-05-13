package com.hris.analytics.controller;

import com.hris.analytics.dto.*;
import com.hris.analytics.enums.AnalyticsScopeType;
import com.hris.analytics.service.AnalyticsQueryService;
import com.hris.analytics.service.AnalyticsScopeService;
import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/v2")
@RequiredArgsConstructor
public class AnalyticsV2Controller {

    private final AnalyticsScopeService analyticsScopeService;
    private final AnalyticsQueryService analyticsQueryService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/scopes")
    public ResponseEntity<ApiResponse<List<AnalyticsScopeOptionDto>>> getScopes(Authentication authentication) {
        UUID userId = authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(analyticsScopeService.getAvailableScopes(userId)));
    }

    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<AnalyticsFiltersDto>> getFilters(Authentication authentication) {
        UUID userId = authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(analyticsScopeService.getFilters(userId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AnalyticsSummaryDto>> getSummary(
            Authentication authentication,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = authorizeRead(authentication);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getSummary(from, to, scopeType, scopeId)));
    }

    @GetMapping("/leave-requests")
    public ResponseEntity<ApiResponse<AnalyticsLeaveRequestReportDto>> getLeaveRequests(
            Authentication authentication,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = authorizeRead(authentication);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getLeaveRequests(from, to, scopeType, scopeId)));
    }

    @GetMapping("/leave-balances")
    public ResponseEntity<ApiResponse<AnalyticsLeaveBalanceReportDto>> getLeaveBalances(
            Authentication authentication,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = authorizeRead(authentication);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getLeaveBalances(from, to, scopeType, scopeId)));
    }

    @GetMapping("/approvals")
    public ResponseEntity<ApiResponse<AnalyticsApprovalReportDto>> getApprovals(
            Authentication authentication,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = authorizeRead(authentication);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getApprovals(from, to, scopeType, scopeId)));
    }

    @GetMapping("/admin-requests")
    public ResponseEntity<ApiResponse<AnalyticsAdminRequestReportDto>> getAdminRequests(
            Authentication authentication,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = authorizeRead(authentication);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getAdminRequests(from, to, scopeType, scopeId)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AnalyticsDashboardDto>> getDashboard(
            Authentication authentication,
            @RequestParam(required = false) AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = authorizeRead(authentication);
        if (scopeType != null) {
            analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        }
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getDashboard(userId, scopeType, scopeId, from, to)));
    }

    private UUID authorizeRead(Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "ANALYTICS_READ_OWN",
            "ANALYTICS_READ_SCOPED",
            "ANALYTICS_READ_GLOBAL",
            "REPORT_READ"
        );
        return SecurityUtils.getCurrentUserId(authentication);
    }
}
