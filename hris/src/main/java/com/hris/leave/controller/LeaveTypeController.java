package com.hris.leave.controller;

import com.hris.common.ApiResponse;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.repository.LeaveTypeRepository;
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

    private final LeaveTypeRepository leaveTypeRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaveType>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(leaveTypeRepository.findByIsActiveTrue()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<LeaveType>> create(@RequestBody LeaveType leaveType) {
        LeaveType saved = leaveTypeRepository.save(leaveType);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(saved));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<LeaveType>> update(
            @PathVariable UUID id,
            @RequestBody LeaveType leaveType) {
        LeaveType existing = leaveTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        existing.setName(leaveType.getName());
        existing.setCode(leaveType.getCode());
        existing.setPaid(leaveType.isPaid());
        existing.setRequiresJustification(leaveType.isRequiresJustification());
        existing.setActive(leaveType.isActive());
        return ResponseEntity.ok(ApiResponse.ok(leaveTypeRepository.save(existing)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        LeaveType existing = leaveTypeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        existing.setActive(false);
        leaveTypeRepository.save(existing);
        return ResponseEntity.noContent().build();
    }
}
