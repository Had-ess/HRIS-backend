package com.hris.approval.service;

import com.hris.access.service.AccessResolutionService;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.enums.AnalyticsEventType;
import com.hris.analytics.service.AnalyticsEventPublisher;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.common.exception.StepAlreadyDecidedException;
import com.hris.settings.validation.entity.ValidationMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final AccessResolutionService accessResolutionService;
    private final List<WorkflowCompletionHandler> completionHandlers;

    @Transactional(readOnly = true)
    public Page<ApprovalStep> getPendingForApprover(UUID approverId, Pageable pageable) {
        return approvalStepRepository.findByApproverIdAndStatusOrderByStepOrderAsc(
            approverId, StepStatus.PENDING, pageable);
    }

    @Transactional
    public void approve(UUID stepId, String comment, UUID approverId) {
        LockedApprovalContext context = loadLockedApprovalContext(stepId);
        ApprovalStep step = context.step();
        ApprovalWorkflow workflow = context.workflow();

        validateWorkflowActive(workflow);
        validateStepOwnership(step, approverId);
        validateLeaveApprovalPermission(workflow, approverId);
        validateStepPending(step);

        step.approve(comment);
        approvalStepRepository.save(step);
        analyticsEventPublisher.publishApprovalEvent(AnalyticsEventType.APPROVAL_STEP_APPROVED, step, workflow);
        auditLogService.log(approverId, AuditAction.APPROVE, "approval_step", step.getId(), null, step);

        evaluateWorkflowAfterApproval(step.getWorkflowId(), approverId);
    }

    @Transactional
    public void approve(UUID stepId, UUID approverId, String comment) {
        approve(stepId, comment, approverId);
    }

    @Transactional
    public void reject(UUID stepId, String comment, UUID approverId) {
        LockedApprovalContext context = loadLockedApprovalContext(stepId);
        ApprovalStep step = context.step();
        ApprovalWorkflow workflow = context.workflow();

        validateWorkflowActive(workflow);
        validateStepOwnership(step, approverId);
        validateLeaveApprovalPermission(workflow, approverId);
        validateStepPending(step);

        step.reject(comment);
        approvalStepRepository.save(step);
        analyticsEventPublisher.publishApprovalEvent(AnalyticsEventType.APPROVAL_STEP_REJECTED, step, workflow);
        auditLogService.log(approverId, AuditAction.REJECT, "approval_step", step.getId(), null, step);

        evaluateWorkflowAfterRejection(step.getWorkflowId(), approverId);
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

    private void validateLeaveApprovalPermission(ApprovalWorkflow workflow, UUID approverId) {
        if (!"LEAVE".equalsIgnoreCase(workflow.getSubjectType())) {
            return;
        }
        if (!accessResolutionService.hasPermissionName(approverId, "LEAVE_REQUEST_APPROVE")) {
            throw new AccessDeniedException("You do not have permission to approve leave requests");
        }
    }

    private LockedApprovalContext loadLockedApprovalContext(UUID stepId) {
        ApprovalStep stepSnapshot = approvalStepRepository.findById(stepId)
            .orElseThrow(() -> new EntityNotFoundException("Approval step not found"));
        ApprovalWorkflow workflow = approvalWorkflowRepository.findByIdForUpdate(stepSnapshot.getWorkflowId())
            .orElseThrow(() -> new EntityNotFoundException("Workflow not found"));
        ApprovalStep step = approvalStepRepository.findByIdForUpdate(stepId)
            .orElseThrow(() -> new EntityNotFoundException("Approval step not found"));
        return new LockedApprovalContext(step, workflow);
    }

    private void validateStepPending(ApprovalStep step) {
        if (!step.isDecidable()) {
            throw new StepAlreadyDecidedException(
                "Step has already been decided: " + step.getStatus());
        }
    }

    private void validateWorkflowActive(ApprovalWorkflow workflow) {
        if (workflow.isComplete()) {
            throw new InvalidWorkflowStateException("Workflow is already completed or rejected");
        }
    }

    private void evaluateWorkflowAfterApproval(UUID workflowId, UUID actorId) {
        ApprovalWorkflow workflow = approvalWorkflowRepository.findByIdForUpdate(workflowId)
            .orElseThrow(() -> new EntityNotFoundException("Workflow not found"));
        List<ApprovalStep> steps = approvalStepRepository.findByWorkflowIdOrderByStepOrder(workflowId);
        ValidationMode mode = workflow.getValidationMode();
        if (mode == null) {
            if (isLegacyWorkflowFullyApproved(steps)) {
                completeWorkflow(workflow, WorkflowStatus.APPROVED, actorId);
            }
            return;
        }

        WorkflowTally tally = WorkflowTally.from(steps);
        switch (mode) {
            case ONE_REQUIRED, INFO_PLUS_PRIMARY -> {
                if (tally.approved() >= 1) {
                    skipPendingSteps(workflowId, "Auto-closed due to workflow approval");
                    completeWorkflow(workflow, WorkflowStatus.APPROVED, actorId);
                }
            }
            case ALL_REQUIRED -> {
                int required = workflow.getRequiredApprovals() == null ? tally.required() : workflow.getRequiredApprovals();
                if (tally.approved() >= required && tally.pending() == 0) {
                    completeWorkflow(workflow, WorkflowStatus.APPROVED, actorId);
                }
            }
            case MIN_N -> {
                int required = workflow.getRequiredApprovals() == null ? 0 : workflow.getRequiredApprovals();
                if (tally.approved() >= required) {
                    skipPendingSteps(workflowId, "Auto-closed due to workflow approval threshold reached");
                    completeWorkflow(workflow, WorkflowStatus.APPROVED, actorId);
                }
            }
        }
    }

    private void evaluateWorkflowAfterRejection(UUID workflowId, UUID actorId) {
        ApprovalWorkflow workflow = approvalWorkflowRepository.findByIdForUpdate(workflowId)
            .orElseThrow(() -> new EntityNotFoundException("Workflow not found"));
        List<ApprovalStep> steps = approvalStepRepository.findByWorkflowIdOrderByStepOrder(workflowId);
        ValidationMode mode = workflow.getValidationMode();
        if (mode == null || mode == ValidationMode.ONE_REQUIRED || mode == ValidationMode.ALL_REQUIRED || mode == ValidationMode.INFO_PLUS_PRIMARY) {
            skipPendingSteps(workflowId, "Auto-closed due to workflow rejection");
            completeWorkflow(workflow, WorkflowStatus.REJECTED, actorId);
            return;
        }

        WorkflowTally tally = WorkflowTally.from(steps);
        int required = workflow.getRequiredApprovals() == null ? 0 : workflow.getRequiredApprovals();
        if (tally.approved() + tally.pending() < required) {
            skipPendingSteps(workflowId, "Auto-closed because approval threshold can no longer be reached");
            completeWorkflow(workflow, WorkflowStatus.REJECTED, actorId);
        }
    }

    private boolean isLegacyWorkflowFullyApproved(List<ApprovalStep> steps) {
        return steps.stream().noneMatch(step -> step.getStatus() == StepStatus.PENDING || step.getStatus() == StepStatus.REJECTED);
    }

    private void skipPendingSteps(UUID workflowId, String comment) {
        List<ApprovalStep> pendingSteps = approvalStepRepository.findByWorkflowIdAndStatus(
            workflowId, StepStatus.PENDING);
        if (pendingSteps.isEmpty()) {
            return;
        }

        pendingSteps.forEach(step -> step.skip(comment));
        approvalStepRepository.saveAll(pendingSteps);
    }

    private void completeWorkflow(ApprovalWorkflow workflow, WorkflowStatus status, UUID actorId) {
        if (workflow.isComplete()) {
            return;
        }

        workflow.setStatus(status);
        workflow.setCompletedAt(Instant.now());
        approvalWorkflowRepository.save(workflow);

        completionHandlers.stream()
            .filter(handler -> handler.supports(workflow.getSubjectType()))
            .findFirst()
            .ifPresent(handler -> handler.handleCompletion(workflow.getSubjectId(), status, actorId));

        log.info("Workflow {} completed with status: {}", workflow.getId(), status);
    }

    private record LockedApprovalContext(ApprovalStep step, ApprovalWorkflow workflow) {
    }

    private record WorkflowTally(int approved, int rejected, int pending, int required) {
        static WorkflowTally from(List<ApprovalStep> steps) {
            int approved = 0;
            int rejected = 0;
            int pending = 0;
            int required = 0;
            for (ApprovalStep step : steps) {
                if (step.getStatus() != StepStatus.INFORMATIONAL) {
                    required++;
                }
                switch (step.getStatus()) {
                    case APPROVED -> approved++;
                    case REJECTED -> rejected++;
                    case PENDING -> pending++;
                    default -> {
                    }
                }
            }
            return new WorkflowTally(approved, rejected, pending, required);
        }
    }
}
