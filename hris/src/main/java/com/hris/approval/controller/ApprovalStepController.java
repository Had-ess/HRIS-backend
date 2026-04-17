package com.hris.approval.controller;

import com.hris.approval.dto.ApprovalDecisionDto;
import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.mapper.ApprovalMapper;
import com.hris.approval.service.ApprovalStepService;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/approval-steps")
@RequiredArgsConstructor
public class ApprovalStepController {

    private final ApprovalStepService approvalStepService;
    private final ApprovalMapper approvalMapper;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PageResponse<ApprovalStepResponseDto>>> getMyPending(
            Pageable pageable, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        Page<ApprovalStep> steps = approvalStepService.getPendingForApprover(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(steps.map(approvalMapper::toStepDto))));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalDecisionDto dto,
            Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        approvalStepService.approve(id, dto.comment(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalDecisionDto dto,
            Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        approvalStepService.reject(id, dto.comment(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
