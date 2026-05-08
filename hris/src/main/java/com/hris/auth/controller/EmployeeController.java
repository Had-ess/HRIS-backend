package com.hris.auth.controller;

import com.hris.auth.dto.EmployeeCreateDto;
import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.dto.EmployeeUpdateDto;
import com.hris.auth.service.EmployeeOnboardingService;
import com.hris.auth.service.EmployeeService;
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
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeOnboardingService employeeOnboardingService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EmployeeResponseDto>>> getAll(
            Pageable pageable,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "READ");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(employeeService.getAll(actorId, pageable))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> create(
            @Valid @RequestBody EmployeeCreateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(employeeOnboardingService.onboard(dto, actorId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "READ");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(employeeService.getById(id, actorId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeUpdateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(employeeService.update(id, dto, actorId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "DELETE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        employeeService.delete(id, actorId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
