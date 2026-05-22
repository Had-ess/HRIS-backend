package com.hris.analytics.controller;

import com.hris.analytics.dto.*;
import com.hris.analytics.service.AnalyticsQueryService;
import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

@RestController
@RequestMapping("/api/analytics/v2")
@RequiredArgsConstructor
public class AnalyticsV2Controller {

    private final AnalyticsQueryService analyticsQueryService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AnalyticsSummaryDto>> getSummary(
            Authentication authentication,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getSummary(from, to)));
    }

    @GetMapping("/leave-requests")
    public ResponseEntity<ApiResponse<AnalyticsLeaveRequestReportDto>> getLeaveRequests(
            Authentication authentication,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getLeaveRequests(from, to)));
    }

    @GetMapping("/leave-balances")
    public ResponseEntity<ApiResponse<AnalyticsLeaveBalanceReportDto>> getLeaveBalances(
            Authentication authentication,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getLeaveBalances(from, to)));
    }

    @GetMapping("/approvals")
    public ResponseEntity<ApiResponse<AnalyticsApprovalReportDto>> getApprovals(
            Authentication authentication,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getApprovals(from, to)));
    }

    @GetMapping("/admin-requests")
    public ResponseEntity<ApiResponse<AnalyticsAdminRequestReportDto>> getAdminRequests(
            Authentication authentication,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getAdminRequests(from, to)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AnalyticsDashboardDto>> getDashboard(
            Authentication authentication,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getDashboard(from, to)));
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AnalyticsOverviewDto>> getOverview(
            Authentication authentication,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getOverview(from, to)));
    }

    @GetMapping("/headcount-trend")
    public ResponseEntity<ApiResponse<List<HeadcountTrendPointDto>>> getHeadcountTrend(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int year) {
        authorizeRead(authentication);
        int resolvedYear = year > 0 ? year : Year.now().getValue();
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getHeadcountTrend(resolvedYear)));
    }

    @GetMapping("/leave-requests/monthly-status")
    public ResponseEntity<ApiResponse<List<LeaveMonthlyStatusPointDto>>> getLeaveMonthlyStatus(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int year) {
        authorizeRead(authentication);
        int resolvedYear = year > 0 ? year : Year.now().getValue();
        return ResponseEntity.ok(ApiResponse.ok(analyticsQueryService.getLeaveMonthlyStatus(resolvedYear)));
    }

    private void authorizeRead(Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "ANALYTICS_READ",
            "REPORT_READ"
        );
    }
}
