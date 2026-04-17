package com.hris.approval.service;

import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.common.exception.StepAlreadyDecidedException;
import com.hris.leave.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalStepService {

    private final ApprovalStepRepository approvalStepRepository;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final AuditLogService auditLogService;
    @Lazy
    private final LeaveRequestService leaveRequestService;

    @Transactional(readOnly = true)
    public Page<ApprovalStep> getPendingForApprover(UUID approverId, Pageable pageable) {
        return approvalStepRepository.findByApproverIdAndStatusOrderByStepOrderAsc(
            approverId, StepStatus.PENDING, pageable);
    }

    @Transactional
    public void approve(UUID stepId, String comment, UUID approverId) {
        ApprovalStep step = approvalStepRepository.findByIdForUpdate(stepId)
            .orElseThrow(() -> new EntityNotFoundException("Approval step not found"));
        ApprovalWorkflow workflow = approvalWorkflowRepository.findById(step.getWorkflowId())
            .orElseThrow(() -> new EntityNotFoundException("Workflow not found"));

        validateWorkflowActive(workflow);
        validateStepOwnership(step, approverId);
        validateStepPending(step);

        step.approve(comment);
        approvalStepRepository.save(step);
        auditLogService.log(approverId, AuditAction.APPROVE, "approval_step", step.getId(), null, step);

        checkWorkflowCompletion(step.getWorkflowId());
    }

    @Transactional
    public void approve(UUID stepId, UUID approverId, String comment) {
        approve(stepId, comment, approverId);
    }

    @Transactional
    public void reject(UUID stepId, String comment, UUID approverId) {
        ApprovalStep step = approvalStepRepository.findByIdForUpdate(stepId)
            .orElseThrow(() -> new EntityNotFoundException("Approval step not found"));
        ApprovalWorkflow workflow = approvalWorkflowRepository.findById(step.getWorkflowId())
            .orElseThrow(() -> new EntityNotFoundException("Workflow not found"));

        validateWorkflowActive(workflow);
        validateStepOwnership(step, approverId);
        validateStepPending(step);

        step.reject(comment);
        approvalStepRepository.save(step);
        auditLogService.log(approverId, AuditAction.REJECT, "approval_step", step.getId(), null, step);

        closePendingSteps(step.getWorkflowId(), "Auto-closed due to workflow rejection");
        completeWorkflow(step.getWorkflowId(), WorkflowStatus.REJECTED);
    }

    @Transactional
    public void reject(UUID stepId, UUID approverId, String comment) {
        reject(stepId, comment, approverId);
    }

    private void validateStepOwnership(ApprovalStep step, UUID approverId) {
        if (!step.getApproverId().equals(approverId)) {
            throw new AccessDeniedException("You are not the assigned approver for this step");
        }
    }

    private void validateStepPending(ApprovalStep step) {
        if (!step.isPending()) {
            throw new StepAlreadyDecidedException(
                "Step has already been decided: " + step.getStatus());
        }
    }

    private void validateWorkflowActive(ApprovalWorkflow workflow) {
        if (workflow.isComplete()) {
            throw new InvalidWorkflowStateException("Workflow is already completed or rejected");
        }
    }

    private void checkWorkflowCompletion(UUID workflowId) {
        if (isWorkflowFullyApproved(workflowId)) {
            completeWorkflow(workflowId, WorkflowStatus.COMPLETED);
        }
    }

    private boolean isWorkflowFullyApproved(UUID workflowId) {
        return approvalStepRepository.countByWorkflowIdAndStatus(workflowId, StepStatus.PENDING) == 0
            && approvalStepRepository.countByWorkflowIdAndStatus(workflowId, StepStatus.REJECTED) == 0;
    }

    private void closePendingSteps(UUID workflowId, String comment) {
        List<ApprovalStep> pendingSteps = approvalStepRepository.findByWorkflowIdAndStatus(
            workflowId, StepStatus.PENDING);
        if (pendingSteps.isEmpty()) {
            return;
        }

        pendingSteps.forEach(step -> step.reject(comment));
        approvalStepRepository.saveAll(pendingSteps);
    }

    private void completeWorkflow(UUID workflowId, WorkflowStatus status) {
        ApprovalWorkflow workflow = approvalWorkflowRepository.findById(workflowId)
            .orElseThrow(() -> new EntityNotFoundException("Workflow not found"));

        if (workflow.isComplete()) {
            return;
        }

        workflow.setStatus(status);
        workflow.setCompletedAt(Instant.now());
        approvalWorkflowRepository.save(workflow);

        if ("LEAVE".equals(workflow.getSubjectType())) {
            leaveRequestService.handleWorkflowCompletion(workflow.getSubjectId(), status);
        }

        log.info("Workflow {} completed with status: {}", workflowId, status);
    }
}
