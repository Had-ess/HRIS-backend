package com.hris.performance.controller;

import com.hris.common.ApiResponse;
import com.hris.performance.dto.PerformanceDtos.CycleCreateDto;
import com.hris.performance.dto.PerformanceDtos.CycleDto;
import com.hris.performance.service.ReviewCycleService;
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
@RequestMapping("/api/performance/cycles")
@RequiredArgsConstructor
public class ReviewCycleController {

    private final ReviewCycleService reviewCycleService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CycleDto>>> getAll(Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(reviewCycleService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CycleDto>> get(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(reviewCycleService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CycleDto>> create(
            @Valid @RequestBody CycleCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(reviewCycleService.create(dto, userId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CycleDto>> update(
            @PathVariable UUID id, @Valid @RequestBody CycleCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewCycleService.update(id, dto, userId)));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<CycleDto>> activate(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewCycleService.activate(id, userId)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<CycleDto>> close(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(reviewCycleService.close(id, userId)));
    }
}
