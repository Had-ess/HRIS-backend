package com.hris.organisation.controller;

import com.hris.common.ApiResponse;
import com.hris.organisation.dto.JobTitleCreateDto;
import com.hris.organisation.dto.JobTitleDto;
import com.hris.organisation.service.JobTitleService;
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
@RequestMapping("/api/job-titles")
@RequiredArgsConstructor
public class JobTitleController {

    private final JobTitleService jobTitleService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    /**
     * Job titles are reference data for the employee create/edit forms, so read
     * access follows the employee permissions (same rule as work schedules).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<JobTitleDto>>> getAll(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly,
            Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "EMPLOYEE_READ",
            "EMPLOYEE_MANAGE"
        );
        return ResponseEntity.ok(ApiResponse.ok(
            activeOnly ? jobTitleService.getAllActive() : jobTitleService.getAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobTitleDto>> create(
            @Valid @RequestBody JobTitleCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "EMPLOYEE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(jobTitleService.create(dto, userId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<JobTitleDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody JobTitleCreateDto dto,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, "EMPLOYEE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(jobTitleService.update(id, dto, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "EMPLOYEE", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        jobTitleService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
