package com.hris.performance.controller;

import com.hris.common.ApiResponse;
import com.hris.performance.dto.PerformanceDtos.HrOverrideDto;
import com.hris.performance.dto.PerformanceDtos.ManagerSubmitDto;
import com.hris.performance.dto.PerformanceDtos.ReviewDto;
import com.hris.performance.dto.PerformanceDtos.SelfSubmitDto;
import com.hris.performance.service.PerformanceReviewService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/performance/reviews")
@RequiredArgsConstructor
public class PerformanceReviewController {

    private final PerformanceReviewService reviewService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> myReviews(Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getMyReviews(SecurityUtils.getCurrentUserId(auth))));
    }

    @GetMapping("/team")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> teamReviews(Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getTeamReviews(SecurityUtils.getCurrentUserId(auth))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewDto>> get(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getReview(id, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/{id}/self-submit")
    public ResponseEntity<ApiResponse<ReviewDto>> selfSubmit(
            @PathVariable UUID id, @Valid @RequestBody SelfSubmitDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            reviewService.selfSubmit(id, dto, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/{id}/manager-submit")
    public ResponseEntity<ApiResponse<ReviewDto>> managerSubmit(
            @PathVariable UUID id, @Valid @RequestBody ManagerSubmitDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            reviewService.managerSubmit(id, dto, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<ApiResponse<ReviewDto>> acknowledge(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            reviewService.acknowledge(id, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/{id}/hr-override")
    public ResponseEntity<ApiResponse<ReviewDto>> hrOverride(
            @PathVariable UUID id, @Valid @RequestBody HrOverrideDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            reviewService.hrOverride(id, dto, SecurityUtils.getCurrentUserId(auth))));
    }
}
