package com.hris.performance.controller;

import com.hris.common.ApiResponse;
import com.hris.performance.dto.PerformanceDtos.CompetencyCreateDto;
import com.hris.performance.dto.PerformanceDtos.CompetencyDto;
import com.hris.performance.service.CompetencyService;
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

/** Competency catalog admin (PERFORMANCE_MANAGE). Per-review competency ratings ride on the review API. */
@RestController
@RequestMapping("/api/performance/competencies")
@RequiredArgsConstructor
public class CompetencyController {

    private final CompetencyService competencyService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompetencyDto>>> getAll(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            activeOnly ? competencyService.getAllActive() : competencyService.getAll()));
    }

    @GetMapping("/job-families")
    public ResponseEntity<ApiResponse<List<String>>> jobFamilies(Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(competencyService.getJobFamilies()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompetencyDto>> get(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(competencyService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompetencyDto>> create(
            @Valid @RequestBody CompetencyCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(competencyService.create(dto, userId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CompetencyDto>> update(
            @PathVariable UUID id, @Valid @RequestBody CompetencyCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(competencyService.update(id, dto, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "PERFORMANCE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        competencyService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
