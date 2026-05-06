package com.hris.auth.controller;

import com.hris.auth.dto.DepartmentCreateDto;
import com.hris.auth.dto.DepartmentDto;
import com.hris.auth.service.DepartmentService;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentDto>> getById(
            @PathVariable UUID id,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, "DEPARTMENT", "READ", "DEPT_MANAGER", "HR_ADMIN", "ADMINISTRATION");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(departmentService.getById(id, userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DepartmentDto>>> getAll(
            Pageable pageable,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, "DEPARTMENT", "READ", "DEPT_MANAGER", "HR_ADMIN", "ADMINISTRATION");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(
            PageResponse.of(departmentService.getAll(userId, pageable))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentDto>> create(
            @Valid @RequestBody DepartmentCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "DEPARTMENT", "CREATE", "ADMINISTRATION");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(departmentService.create(dto, userId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody DepartmentCreateDto dto,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, "DEPARTMENT", "UPDATE", "ADMINISTRATION");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(departmentService.update(id, dto, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "DEPARTMENT", "DELETE", "ADMINISTRATION");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        departmentService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<DepartmentDto>> deactivate(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "DEPARTMENT", "DEACTIVATE", "ADMINISTRATION");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(departmentService.deactivate(id, userId)));
    }
}
