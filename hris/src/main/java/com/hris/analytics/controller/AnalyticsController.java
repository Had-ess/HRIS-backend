package com.hris.analytics.controller;

import com.hris.analytics.dto.AbsenceImpactDto;
import com.hris.analytics.dto.AuditLogDto;
import com.hris.analytics.dto.HeadcountMetricsDto;
import com.hris.analytics.dto.LeaveMetricsDto;
import com.hris.analytics.dto.LeaveTrendPointDto;
import com.hris.analytics.dto.LeaveTypeDistributionDto;
import com.hris.analytics.service.AnalyticsService;
import com.hris.analytics.service.AuditLogQueryService;
import com.hris.analytics.service.ScopeFilterResolver;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.common.ScopeFilter;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ScopeFilterResolver scopeFilterResolver;
    private final AuditLogQueryService auditLogQueryService;

    @GetMapping("/leave-metrics")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER', 'PROJECT_SUPERVISOR', 'HR_ADMIN', 'DIRECTOR', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<LeaveMetricsDto>> getLeaveMetrics(
            Authentication auth,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        ScopeFilter scope = scopeFilterResolver.resolve(userId);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getLeaveMetrics(scope, from, to)));
    }

    @GetMapping("/headcount")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER', 'PROJECT_SUPERVISOR', 'HR_ADMIN', 'DIRECTOR', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<HeadcountMetricsDto>> getHeadcountMetrics(
            Authentication auth,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        ScopeFilter scope = scopeFilterResolver.resolve(userId);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getHeadcountMetrics(scope, from, to)));
    }

    @GetMapping("/absence-impact")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER', 'PROJECT_SUPERVISOR', 'HR_ADMIN', 'DIRECTOR', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<List<AbsenceImpactDto>>> getAbsenceImpact(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        ScopeFilter scope = scopeFilterResolver.resolve(userId);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getAbsenceImpact(scope)));
    }

    @GetMapping("/leave-distribution")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER', 'PROJECT_SUPERVISOR', 'HR_ADMIN', 'DIRECTOR', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<List<LeaveTypeDistributionDto>>> getLeaveDistribution(
            Authentication auth,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        ScopeFilter scope = scopeFilterResolver.resolve(userId);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getLeaveTypeDistribution(scope, from, to)));
    }

    @GetMapping("/leave-trend")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER', 'PROJECT_SUPERVISOR', 'HR_ADMIN', 'DIRECTOR', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<List<LeaveTrendPointDto>>> getLeaveTrend(
            Authentication auth,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        ScopeFilter scope = scopeFilterResolver.resolve(userId);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getLeaveTrend(scope, from, to)));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogDto>>> getAuditLogs(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(auditLogQueryService.getAll(pageable))));
    }

    @GetMapping("/audit-logs/by-resource")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogDto>>> getByResource(
            @RequestParam String resource, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
            auditLogQueryService.getByResource(resource, pageable))));
    }
}
