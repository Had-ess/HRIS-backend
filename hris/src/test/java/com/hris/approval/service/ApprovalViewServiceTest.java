package com.hris.approval.service;

import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalViewService Unit Tests")
class ApprovalViewServiceTest {

    @Mock
    private ApprovalWorkflowRepository approvalWorkflowRepository;

    @Mock
    private ApprovalStepRepository approvalStepRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("getStepsForSubject resolves approver names and preserves approval metadata")
    void getStepsForSubjectResolvesApproverNamesAndPreservesApprovalMetadata() {
        UUID subjectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
            .id(workflowId)
            .subjectType("LEAVE")
            .subjectId(subjectId)
            .status(WorkflowStatus.IN_PROGRESS)
            .createdAt(Instant.now())
            .build();
        ApprovalStep step = ApprovalStep.builder()
            .id(UUID.randomUUID())
            .workflowId(workflowId)
            .approverId(approverId)
            .stepOrder(1)
            .status(StepStatus.PENDING)
            .context(ApprovalContext.PROJECT)
            .routingSnapshot("{\"role\":\"PROJECT_SUPERVISOR\"}")
            .comment(null)
            .build();
        ApprovalViewService service = new ApprovalViewService(
            approvalWorkflowRepository,
            approvalStepRepository,
            userRepository
        );

        when(approvalWorkflowRepository.findBySubjectTypeAndSubjectId("LEAVE", subjectId))
            .thenReturn(Optional.of(workflow));
        when(approvalStepRepository.findByWorkflowIdOrderByStepOrder(workflowId))
            .thenReturn(List.of(step));
        when(userRepository.findAllById(any()))
            .thenReturn(List.of(User.builder()
                .id(approverId)
                .firstName("Jane")
                .lastName("Supervisor")
                .email("jane@hris.local")
                .build()));

        List<ApprovalStepResponseDto> result = service.getStepsForSubject("LEAVE", subjectId);

        assertThat(result).hasSize(1);
        ApprovalStepResponseDto dto = result.getFirst();
        assertThat(dto.subjectType()).isEqualTo("LEAVE");
        assertThat(dto.subjectId()).isEqualTo(subjectId);
        assertThat(dto.approverName()).isEqualTo("Jane Supervisor");
        assertThat(dto.context()).isEqualTo(ApprovalContext.PROJECT);
        assertThat(dto.routingSnapshot()).contains("PROJECT_SUPERVISOR");
    }

    @Test
    @DisplayName("getStepsForSubject returns empty list when workflow is missing")
    void getStepsForSubjectReturnsEmptyListWhenWorkflowMissing() {
        UUID subjectId = UUID.randomUUID();
        ApprovalViewService service = new ApprovalViewService(
            approvalWorkflowRepository,
            approvalStepRepository,
            userRepository
        );

        when(approvalWorkflowRepository.findBySubjectTypeAndSubjectId("LEAVE", subjectId))
            .thenReturn(Optional.empty());

        List<ApprovalStepResponseDto> result = service.getStepsForSubject("LEAVE", subjectId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getStepsForSubjects falls back when an approver account no longer exists")
    void getStepsForSubjectsFallsBackWhenApproverAccountNoLongerExists() {
        UUID subjectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID missingApproverId = UUID.randomUUID();
        ApprovalWorkflow workflow = ApprovalWorkflow.builder()
            .id(workflowId)
            .subjectType("LEAVE")
            .subjectId(subjectId)
            .status(WorkflowStatus.REJECTED)
            .createdAt(Instant.now())
            .build();
        ApprovalStep step = ApprovalStep.builder()
            .id(UUID.randomUUID())
            .workflowId(workflowId)
            .approverId(missingApproverId)
            .stepOrder(2)
            .status(StepStatus.REJECTED)
            .context(ApprovalContext.DEPARTMENT)
            .routingSnapshot("{\"role\":\"DEPT_HEAD\"}")
            .comment("Insufficient coverage")
            .decidedAt(Instant.now())
            .build();
        ApprovalViewService service = new ApprovalViewService(
            approvalWorkflowRepository,
            approvalStepRepository,
            userRepository
        );

        when(approvalWorkflowRepository.findBySubjectTypeAndSubjectIdIn("LEAVE", List.of(subjectId)))
            .thenReturn(List.of(workflow));
        when(approvalStepRepository.findByWorkflowIdInOrderByWorkflowIdAscStepOrderAsc(List.of(workflowId)))
            .thenReturn(List.of(step));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        Map<UUID, List<ApprovalStepResponseDto>> result = service.getStepsForSubjects("LEAVE", List.of(subjectId));

        assertThat(result).containsKey(subjectId);
        assertThat(result.get(subjectId)).hasSize(1);
        assertThat(result.get(subjectId).getFirst().approverName()).isEqualTo("Unavailable approver");
        verify(userRepository).findAllById(any());
    }
}
