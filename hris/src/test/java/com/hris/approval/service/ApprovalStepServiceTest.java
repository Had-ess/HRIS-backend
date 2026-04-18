package com.hris.approval.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.common.exception.StepAlreadyDecidedException;
import com.hris.leave.service.LeaveRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalStepService Unit Tests")
class ApprovalStepServiceTest {

    @Mock private ApprovalStepRepository approvalStepRepository;
    @Mock private ApprovalWorkflowRepository approvalWorkflowRepository;
    @Mock private LeaveRequestService leaveRequestService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ApprovalStepService approvalStepService;

    private UUID approverId;
    private UUID stepId;
    private UUID workflowId;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        approverId = UUID.randomUUID();
        stepId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("approve()")
    class ApproveTests {

        @Test
        @DisplayName("should approve step without completing workflow while pending steps remain")
        void shouldApproveStep_Successfully() {
            ApprovalStep step = ApprovalStep.builder()
                .id(stepId)
                .workflowId(workflowId)
                .approverId(approverId)
                .status(StepStatus.PENDING)
                .build();
            ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .id(workflowId)
                .status(WorkflowStatus.IN_PROGRESS)
                .build();

            when(approvalStepRepository.findByIdForUpdate(stepId)).thenReturn(Optional.of(step));
            when(approvalStepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(approvalWorkflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));
            when(approvalStepRepository.countByWorkflowIdAndStatus(workflowId, StepStatus.PENDING)).thenReturn(1L);

            approvalStepService.approve(stepId, approverId, "Looks good");

            assertThat(step.getStatus()).isEqualTo(StepStatus.APPROVED);
            assertThat(step.getComment()).isEqualTo("Looks good");
            assertThat(step.getDecidedAt()).isNotNull();

            verify(approvalStepRepository).save(step);
            verify(approvalWorkflowRepository, never()).save(any());
            verify(auditLogService).log(eq(approverId), eq(com.hris.analytics.enums.AuditAction.APPROVE),
                eq("approval_step"), eq(stepId), isNull(), eq(step));
        }

        @Test
        @DisplayName("should complete workflow only when all steps are approved")
        void shouldCompleteWorkflow_WhenAllStepsApproved() {
            ApprovalStep step = ApprovalStep.builder()
                .id(stepId)
                .workflowId(workflowId)
                .approverId(approverId)
                .status(StepStatus.PENDING)
                .build();

            ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .id(workflowId)
                .subjectType("LEAVE")
                .subjectId(subjectId)
                .status(WorkflowStatus.IN_PROGRESS)
                .build();

            when(approvalStepRepository.findByIdForUpdate(stepId)).thenReturn(Optional.of(step));
            when(approvalStepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(approvalWorkflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));
            when(approvalStepRepository.countByWorkflowIdAndStatus(workflowId, StepStatus.PENDING)).thenReturn(0L);
            when(approvalStepRepository.countByWorkflowIdAndStatus(workflowId, StepStatus.REJECTED)).thenReturn(0L);

            approvalStepService.approve(stepId, approverId, "Approved");

            assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
            assertThat(workflow.getCompletedAt()).isNotNull();

            verify(approvalWorkflowRepository).save(workflow);
            verify(leaveRequestService).handleWorkflowCompletion(subjectId, WorkflowStatus.COMPLETED);
        }

        @Test
        @DisplayName("should throw AccessDeniedException when wrong approver")
        void shouldThrow_WhenWrongApprover() {
            UUID otherUserId = UUID.randomUUID();

            ApprovalStep step = ApprovalStep.builder()
                .id(stepId)
                .workflowId(workflowId)
                .approverId(otherUserId)
                .status(StepStatus.PENDING)
                .build();
            ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .id(workflowId)
                .status(WorkflowStatus.IN_PROGRESS)
                .build();

            when(approvalStepRepository.findByIdForUpdate(stepId)).thenReturn(Optional.of(step));
            when(approvalStepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(approvalWorkflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));

            assertThatThrownBy(() -> approvalStepService.approve(stepId, approverId, "ok"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You are not the assigned approver for this step");
        }

        @Test
        @DisplayName("should throw StepAlreadyDecidedException when step not pending")
        void shouldThrow_WhenStepAlreadyDecided() {
            ApprovalStep step = ApprovalStep.builder()
                .id(stepId)
                .workflowId(workflowId)
                .approverId(approverId)
                .status(StepStatus.APPROVED)
                .build();
            ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .id(workflowId)
                .status(WorkflowStatus.IN_PROGRESS)
                .build();

            when(approvalStepRepository.findByIdForUpdate(stepId)).thenReturn(Optional.of(step));
            when(approvalStepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(approvalWorkflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));

            assertThatThrownBy(() -> approvalStepService.approve(stepId, approverId, "ok"))
                .isInstanceOf(StepAlreadyDecidedException.class);
        }

        @Test
        @DisplayName("should throw EntityNotFoundException when step not found")
        void shouldThrow_WhenStepNotFound() {
            when(approvalStepRepository.findById(stepId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> approvalStepService.approve(stepId, approverId, "ok"))
                .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("should block approval after cancellation closes workflow")
        void shouldThrowApprove_WhenWorkflowClosedByCancellation() {
            ApprovalStep step = ApprovalStep.builder()
                .id(stepId)
                .workflowId(workflowId)
                .approverId(approverId)
                .status(StepStatus.REJECTED)
                .build();
            ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .id(workflowId)
                .status(WorkflowStatus.REJECTED)
                .build();

            when(approvalStepRepository.findByIdForUpdate(stepId)).thenReturn(Optional.of(step));
            when(approvalStepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(approvalWorkflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));

            assertThatThrownBy(() -> approvalStepService.approve(stepId, approverId, "ok"))
                .isInstanceOf(InvalidWorkflowStateException.class)
                .hasMessage("Workflow is already completed or rejected");
        }
    }

    @Nested
    @DisplayName("reject()")
    class RejectTests {

        @Test
        @DisplayName("should reject step, auto-close siblings, and reject workflow")
        void shouldRejectStep_AndRejectWorkflow() {
            ApprovalStep step = ApprovalStep.builder()
                .id(stepId)
                .workflowId(workflowId)
                .approverId(approverId)
                .status(StepStatus.PENDING)
                .build();

            ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .id(workflowId)
                .subjectType("LEAVE")
                .subjectId(subjectId)
                .status(WorkflowStatus.IN_PROGRESS)
                .build();
            ApprovalStep siblingStep = ApprovalStep.builder()
                .id(UUID.randomUUID())
                .workflowId(workflowId)
                .approverId(UUID.randomUUID())
                .status(StepStatus.PENDING)
                .build();

            when(approvalStepRepository.findByIdForUpdate(stepId)).thenReturn(Optional.of(step));
            when(approvalStepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(approvalWorkflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));
            when(approvalStepRepository.findByWorkflowIdAndStatus(workflowId, StepStatus.PENDING))
                .thenReturn(List.of(siblingStep));

            approvalStepService.reject(stepId, approverId, "Not justified");

            assertThat(step.getStatus()).isEqualTo(StepStatus.REJECTED);
            assertThat(step.getComment()).isEqualTo("Not justified");
            assertThat(siblingStep.getStatus()).isEqualTo(StepStatus.REJECTED);
            assertThat(siblingStep.getComment()).isEqualTo("Auto-closed due to workflow rejection");
            assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.REJECTED);
            assertThat(workflow.getCompletedAt()).isNotNull();

            verify(approvalStepRepository).saveAll(List.of(siblingStep));
            verify(leaveRequestService).handleWorkflowCompletion(subjectId, WorkflowStatus.REJECTED);
        }

        @Test
        @DisplayName("should throw when wrong approver tries to reject")
        void shouldThrow_WhenWrongApproverRejects() {
            UUID otherUserId = UUID.randomUUID();

            ApprovalStep step = ApprovalStep.builder()
                .id(stepId)
                .workflowId(workflowId)
                .approverId(otherUserId)
                .status(StepStatus.PENDING)
                .build();
            ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .id(workflowId)
                .status(WorkflowStatus.IN_PROGRESS)
                .build();

            when(approvalStepRepository.findByIdForUpdate(stepId)).thenReturn(Optional.of(step));
            when(approvalStepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(approvalWorkflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));

            assertThatThrownBy(() -> approvalStepService.reject(stepId, approverId, "no"))
                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("should block rejection after cancellation closes workflow")
        void shouldThrowReject_WhenWorkflowClosedByCancellation() {
            ApprovalStep step = ApprovalStep.builder()
                .id(stepId)
                .workflowId(workflowId)
                .approverId(approverId)
                .status(StepStatus.REJECTED)
                .build();
            ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .id(workflowId)
                .status(WorkflowStatus.REJECTED)
                .build();

            when(approvalStepRepository.findByIdForUpdate(stepId)).thenReturn(Optional.of(step));
            when(approvalStepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(approvalWorkflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));

            assertThatThrownBy(() -> approvalStepService.reject(stepId, approverId, "no"))
                .isInstanceOf(InvalidWorkflowStateException.class)
                .hasMessage("Workflow is already completed or rejected");
        }

        @Test
        @DisplayName("should throw StepAlreadyDecidedException when rejecting a decided step")
        void shouldThrow_WhenRejectingAlreadyDecidedStep() {
            ApprovalStep step = ApprovalStep.builder()
                .id(stepId)
                .workflowId(workflowId)
                .approverId(approverId)
                .status(StepStatus.REJECTED)
                .build();
            ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                .id(workflowId)
                .status(WorkflowStatus.IN_PROGRESS)
                .build();

            when(approvalStepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(approvalStepRepository.findByIdForUpdate(stepId)).thenReturn(Optional.of(step));
            when(approvalWorkflowRepository.findByIdForUpdate(workflowId)).thenReturn(Optional.of(workflow));

            assertThatThrownBy(() -> approvalStepService.reject(stepId, approverId, "no"))
                .isInstanceOf(StepAlreadyDecidedException.class)
                .hasMessage("Step has already been decided: REJECTED");
        }
    }
}
