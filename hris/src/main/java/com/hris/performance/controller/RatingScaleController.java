package com.hris.performance.controller;

import com.hris.common.ApiResponse;
import com.hris.performance.dto.PerformanceDtos.RatingScaleCreateDto;
import com.hris.performance.dto.PerformanceDtos.RatingScaleDto;
import com.hris.performance.service.RatingScaleService;
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
@RequestMapping("/api/performance/rating-scales")
@RequiredArgsConstructor
public class RatingScaleController {

    private final RatingScaleService ratingScaleService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RatingScaleDto>>> getAll(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            activeOnly ? ratingScaleService.getAllActive() : ratingScaleService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RatingScaleDto>> get(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(ratingScaleService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RatingScaleDto>> create(
            @Valid @RequestBody RatingScaleCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(ratingScaleService.create(dto, userId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<RatingScaleDto>> update(
            @PathVariable UUID id, @Valid @RequestBody RatingScaleCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(ratingScaleService.update(id, dto, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        ratingScaleService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
