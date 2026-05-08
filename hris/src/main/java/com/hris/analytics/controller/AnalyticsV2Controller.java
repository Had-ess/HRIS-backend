package com.hris.analytics.controller;

import com.hris.analytics.dto.*;
import com.hris.analytics.enums.AnalyticsScopeType;
import com.hris.analytics.service.AnalyticsQueryService;
import com.hris.analytics.service.AnalyticsScopeService;
import com.hris.common.ApiResponse;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/scopes")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'ANALYTICS', 'READ')")
    public ResponseEntity<ApiResponse<List<AnalyticsScopeOptionDto>>> getScopes(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(analyticsScopeService.getAvailableScopes(userId)));
    }

    @GetMapping("/leave-metrics")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'ANALYTICS', 'READ')")
    public ResponseEntity<ApiResponse<LeaveMetricsSnapshotDto>> getLeaveMetrics(
            Authentication auth,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate date) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(
            analyticsQueryService.getLeaveMetrics(date != null ? date : LocalDate.now(), scopeType, scopeId)));
    }

    @GetMapping("/headcount")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'ANALYTICS', 'READ')")
    public ResponseEntity<ApiResponse<HeadcountMetricsSnapshotDto>> getHeadcount(
            Authentication auth,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate date) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(
            analyticsQueryService.getHeadcountMetrics(date != null ? date : LocalDate.now(), scopeType, scopeId)));
    }

    @GetMapping("/leave-distribution")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'ANALYTICS', 'READ')")
    public ResponseEntity<ApiResponse<List<LeaveDistributionSnapshotDto>>> getLeaveDistribution(
            Authentication auth,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate date) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(
            analyticsQueryService.getLeaveDistribution(date != null ? date : LocalDate.now(), scopeType, scopeId)));
    }

    @GetMapping("/approval-bottlenecks")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'ANALYTICS', 'READ')")
    public ResponseEntity<ApiResponse<List<ApprovalBottleneckSnapshotDto>>> getApprovalBottlenecks(
            Authentication auth,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate date) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(
            analyticsQueryService.getApprovalBottlenecks(date != null ? date : LocalDate.now(), scopeType, scopeId)));
    }

    @GetMapping("/project-absence")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'ANALYTICS', 'READ')")
    public ResponseEntity<ApiResponse<List<ProjectAbsenceFactDto>>> getProjectAbsence(
            Authentication auth,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate date) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(
            analyticsQueryService.getProjectAbsence(date != null ? date : LocalDate.now(), scopeType, scopeId)));
    }

    @GetMapping("/leave-metrics/timeseries")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'ANALYTICS', 'READ')")
    public ResponseEntity<ApiResponse<List<LeaveMetricsTimeseriesPointDto>>> getLeaveMetricsTimeseries(
            Authentication auth,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(
            analyticsQueryService.getLeaveMetricsTimeseries(from, to, scopeType, scopeId)));
    }

    @GetMapping("/headcount/timeseries")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'ANALYTICS', 'READ')")
    public ResponseEntity<ApiResponse<List<HeadcountMetricsTimeseriesPointDto>>> getHeadcountTimeseries(
            Authentication auth,
            @RequestParam AnalyticsScopeType scopeType,
            @RequestParam(required = false) UUID scopeId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        analyticsScopeService.assertAccessible(userId, scopeType, scopeId);
        return ResponseEntity.ok(ApiResponse.ok(
            analyticsQueryService.getHeadcountMetricsTimeseries(from, to, scopeType, scopeId)));
    }
}
