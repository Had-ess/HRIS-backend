package com.hris.approval.controller;

import com.hris.approval.dto.ApprovalCommentDto;
import com.hris.approval.dto.ApprovalDecisionDto;
import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.approval.service.ApprovalStepService;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/approval-steps")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ApprovalStepController {

    private final ApprovalStepService approvalStepService;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final UserRepository userRepository;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PageResponse<ApprovalStepResponseDto>>> getMyPending(
            Pageable pageable, Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        Page<ApprovalStep> steps = approvalStepService.getPendingForApprover(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(steps.map(this::toStepDto))));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApprovalCommentDto dto,
            Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        approvalStepService.approve(id, dto != null ? dto.comment() : null, userId);
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

    private ApprovalStepResponseDto toStepDto(ApprovalStep step) {
        ApprovalWorkflow workflow = approvalWorkflowRepository.findById(step.getWorkflowId()).orElse(null);

        return new ApprovalStepResponseDto(
            step.getId(),
            step.getWorkflowId(),
            workflow != null ? workflow.getSubjectType() : null,
            workflow != null ? workflow.getSubjectId() : null,
            step.getApproverId(),
            resolveApproverName(step.getApproverId()),
            step.getStepOrder(),
            step.getStatus(),
            step.getContext(),
            step.getRoutingSnapshot(),
            step.getComment(),
            step.getDecidedAt()
        );
    }

    private String resolveApproverName(UUID approverId) {
        return userRepository.findById(approverId)
            .map(this::toDisplayName)
            .orElse(null);
    }

    private String toDisplayName(User user) {
        String fullName = (user.getFirstName() + " " + user.getLastName()).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }
}
