package com.hris.leave.controller;

import com.hris.common.ApiResponse;
import com.hris.leave.dto.LeaveTypeCreateDto;
import com.hris.leave.dto.LeaveTypeDto;
import com.hris.leave.dto.LeaveTypeUpdateDto;
import com.hris.leave.service.LeaveTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave-types")
@RequiredArgsConstructor
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaveTypeDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(leaveTypeService.getAllActive()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<LeaveTypeDto>> create(
            @Valid @RequestBody LeaveTypeCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(leaveTypeService.create(dto)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<LeaveTypeDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody LeaveTypeUpdateDto dto) {
        return ResponseEntity.ok(ApiResponse.ok(leaveTypeService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        leaveTypeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
