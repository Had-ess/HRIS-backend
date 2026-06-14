package com.hris.performance.controller;

import com.hris.common.ApiResponse;
import com.hris.performance.dto.PerformanceDtos.CalibrationAdjustDto;
import com.hris.performance.dto.PerformanceDtos.CalibrationGridDto;
import com.hris.performance.service.CalibrationService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 9-box calibration — HR/admin only (PERFORMANCE/MANAGE). The service additionally scope-filters
 * reviews to the actor's department data scope. Placement is never exposed to the subject.
 */
@RestController
@RequestMapping("/api/performance/calibration")
@RequiredArgsConstructor
public class CalibrationController {

    private final CalibrationService calibrationService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/cycles/{cycleId}/grid")
    public ResponseEntity<ApiResponse<CalibrationGridDto>> grid(@PathVariable UUID cycleId, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            calibrationService.getGrid(cycleId, SecurityUtils.getCurrentUserId(auth))));
    }

    @PostMapping("/reviews/{reviewId}/adjust")
    public ResponseEntity<ApiResponse<CalibrationGridDto>> adjust(
            @PathVariable UUID reviewId, @Valid @RequestBody CalibrationAdjustDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            calibrationService.adjust(reviewId, dto, SecurityUtils.getCurrentUserId(auth))));
    }
}
