package com.hris.auth.controller;

import com.hris.auth.dto.EmployeeCreateDto;
import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.dto.EmployeeUpdateDto;
import com.hris.auth.service.EmployeeOnboardingService;
import com.hris.auth.service.EmployeeService;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeOnboardingService employeeOnboardingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeResponseDto>>> getAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(employeeService.getAll(pageable))));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATION')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> create(
            @Valid @RequestBody EmployeeCreateDto dto,
            Authentication authentication) {
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(employeeOnboardingService.onboard(dto, actorId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(employeeService.getById(id)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeUpdateDto dto,
            Authentication authentication) {
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(employeeService.update(id, dto, actorId)));
    }
}
