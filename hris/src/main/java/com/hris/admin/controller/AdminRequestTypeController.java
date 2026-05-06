package com.hris.admin.controller;

import com.hris.admin.dto.AdminRequestTypeCreateDto;
import com.hris.admin.dto.AdminRequestTypeDto;
import com.hris.admin.dto.AdminRequestTypeUpdateDto;
import com.hris.admin.service.AdminRequestTypeService;
import com.hris.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin-request-types")
@RequiredArgsConstructor
public class AdminRequestTypeController {

    private final AdminRequestTypeService adminRequestTypeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminRequestTypeDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(adminRequestTypeService.getAll()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<AdminRequestTypeDto>> create(
            @Valid @RequestBody AdminRequestTypeCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(adminRequestTypeService.create(dto)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<AdminRequestTypeDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRequestTypeUpdateDto dto) {
        return ResponseEntity.ok(ApiResponse.ok(adminRequestTypeService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        adminRequestTypeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
