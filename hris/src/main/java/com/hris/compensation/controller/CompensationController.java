package com.hris.compensation.controller;

import com.hris.common.ApiResponse;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordCreateDto;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordDto;
import com.hris.compensation.dto.CompensationDtos.MyCompensationDto;
import com.hris.compensation.dto.CompensationDtos.PayGradeCreateDto;
import com.hris.compensation.dto.CompensationDtos.PayGradeDto;
import com.hris.compensation.service.CompensationService;
import com.hris.compensation.service.PayGradeService;
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
@RequestMapping("/api/compensation")
@RequiredArgsConstructor
public class CompensationController {

    private final CompensationService compensationService;
    private final PayGradeService payGradeService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    // --- Self-view ------------------------------------------------------------

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyCompensationDto>> myCompensation(Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "VIEW_OWN");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(compensationService.getMyCompensation(userId)));
    }

    // --- Pay grades (HR) ------------------------------------------------------

    @GetMapping("/grades")
    public ResponseEntity<ApiResponse<List<PayGradeDto>>> getGrades(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly,
            Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(payGradeService.getAll(activeOnly)));
    }

    @PostMapping("/grades")
    public ResponseEntity<ApiResponse<PayGradeDto>> createGrade(
            @Valid @RequestBody PayGradeCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(payGradeService.create(dto, userId)));
    }

    @PatchMapping("/grades/{id}")
    public ResponseEntity<ApiResponse<PayGradeDto>> updateGrade(
            @PathVariable UUID id, @Valid @RequestBody PayGradeCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(payGradeService.update(id, dto, userId)));
    }

    @DeleteMapping("/grades/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGrade(@PathVariable UUID id, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        payGradeService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // --- Employee compensation records (HR) -----------------------------------

    @GetMapping("/employees/{employeeId}/records")
    public ResponseEntity<ApiResponse<List<CompensationRecordDto>>> getRecords(
            @PathVariable UUID employeeId, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(compensationService.listRecords(employeeId, userId)));
    }

    @PostMapping("/employees/{employeeId}/records")
    public ResponseEntity<ApiResponse<CompensationRecordDto>> addRecord(
            @PathVariable UUID employeeId,
            @Valid @RequestBody CompensationRecordCreateDto dto, Authentication auth) {
        permissionAuthorizationService.authorize(auth, "COMPENSATION", "MANAGE");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(compensationService.addRecord(employeeId, dto, userId)));
    }
}
