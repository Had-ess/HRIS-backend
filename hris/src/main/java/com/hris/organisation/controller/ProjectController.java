package com.hris.organisation.controller;

import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.organisation.dto.ProjectAssignmentCreateDto;
import com.hris.organisation.dto.ProjectAssignmentResponseDto;
import com.hris.organisation.dto.ProjectCreateDto;
import com.hris.organisation.dto.ProjectResponseDto;
import com.hris.organisation.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponseDto>>> getAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(projectService.getAll(pageable))));
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> create(
            @Valid @RequestBody ProjectCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(projectService.create(dto)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getById(id)));
    }

    @PostMapping("/{id}/assignments")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<ProjectAssignmentResponseDto>> assignEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectAssignmentCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(projectService.assignEmployee(id, dto)));
    }

    @DeleteMapping("/{id}/assignments/{asgId}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeAssignment(
            @PathVariable UUID id,
            @PathVariable UUID asgId) {
        projectService.removeAssignment(id, asgId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
