package com.hris.performance.controller;

import com.hris.common.ApiResponse;
import com.hris.performance.dto.PerformanceDtos.CheckinCreateDto;
import com.hris.performance.dto.PerformanceDtos.CheckinDto;
import com.hris.performance.dto.PerformanceDtos.GoalCreateDto;
import com.hris.performance.dto.PerformanceDtos.GoalDto;
import com.hris.performance.dto.PerformanceDtos.GoalUpdateDto;
import com.hris.performance.service.PerformanceGoalService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/performance/goals")
@RequiredArgsConstructor
public class PerformanceGoalController {

    private final PerformanceGoalService goalService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalDto>>> myGoals(
            @RequestParam(name = "cycleId", required = false) UUID cycleId, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            goalService.getMyGoals(SecurityUtils.getCurrentUserId(auth), cycleId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GoalDto>> create(
            @Valid @RequestBody GoalCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(goalService.createGoal(SecurityUtils.getCurrentUserId(auth), dto)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<GoalDto>> update(
            @PathVariable UUID id, @Valid @RequestBody GoalUpdateDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            goalService.updateGoal(SecurityUtils.getCurrentUserId(auth), id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        goalService.deleteGoal(SecurityUtils.getCurrentUserId(auth), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{id}/checkins")
    public ResponseEntity<ApiResponse<List<CheckinDto>>> checkins(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            goalService.getCheckins(SecurityUtils.getCurrentUserId(auth), id)));
    }

    @PostMapping("/{id}/checkins")
    public ResponseEntity<ApiResponse<CheckinDto>> addCheckin(
            @PathVariable UUID id, @Valid @RequestBody CheckinCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(auth, "PERFORMANCE_READ", "PERFORMANCE_MANAGE");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(goalService.addCheckin(SecurityUtils.getCurrentUserId(auth), id, dto)));
    }
}
