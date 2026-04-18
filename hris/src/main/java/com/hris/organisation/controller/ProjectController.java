package com.hris.organisation.controller;

import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.organisation.dto.ProjectAssignmentCreateDto;
import com.hris.organisation.dto.ProjectAssignmentResponseDto;
import com.hris.organisation.dto.ProjectCreateDto;
import com.hris.organisation.dto.ProjectDepartmentAssignDto;
import com.hris.organisation.dto.ProjectDepartmentResponseDto;
import com.hris.organisation.dto.ProjectResponseDto;
import com.hris.organisation.service.ProjectService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponseDto>>> getAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(projectService.getAll(pageable))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponseDto>> create(
            @Valid @RequestBody ProjectCreateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "PROJECT", "UPDATE", "HR_ADMIN");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(projectService.create(dto, actorId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getById(id)));
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<ApiResponse<ProjectAssignmentResponseDto>> assignEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectAssignmentCreateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "PROJECT", "UPDATE", "HR_ADMIN");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(projectService.assignEmployee(id, dto, actorId)));
    }

    @DeleteMapping("/{id}/assignments/{asgId}")
    public ResponseEntity<ApiResponse<Void>> removeAssignment(
            @PathVariable UUID id,
            @PathVariable UUID asgId,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "PROJECT", "UPDATE", "HR_ADMIN");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        projectService.removeAssignment(id, asgId, actorId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{id}/departments")
    public ResponseEntity<ApiResponse<List<ProjectDepartmentResponseDto>>> getDepartments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getDepartments(id)));
    }

    @PostMapping("/{id}/departments")
    public ResponseEntity<ApiResponse<ProjectDepartmentResponseDto>> assignDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDepartmentAssignDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "PROJECT", "UPDATE", "HR_ADMIN");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(projectService.assignDepartment(id, dto, actorId)));
    }

    @DeleteMapping("/{id}/departments/{departmentId}")
    public ResponseEntity<ApiResponse<Void>> removeDepartment(
            @PathVariable UUID id,
            @PathVariable UUID departmentId,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "PROJECT", "UPDATE", "HR_ADMIN");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        projectService.removeDepartment(id, departmentId, actorId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
