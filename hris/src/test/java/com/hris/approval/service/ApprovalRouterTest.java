package com.hris.approval.service;

import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.common.exception.InvalidWorkflowStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalRouter Unit Tests")
class ApprovalRouterTest {

    @Mock
    private ApprovalRouteResolver approvalRouteResolver;

    @Mock
    private ApprovalStepRepository approvalStepRepository;

    @Mock
    private ApprovalStepFactory approvalStepFactory;

    private ApprovalRouter approvalRouter;
    private UUID requesterId;
    private UUID workflowId;

    @BeforeEach
    void setUp() {
        approvalRouter = new ApprovalRouter(
            approvalRouteResolver,
            approvalStepFactory,
            approvalStepRepository
        );
        requesterId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
    }

    @Test
    @DisplayName("resolveSteps persists ordered approval steps from the route plan")
    void resolveStepsPersistsOrderedApprovalStepsFromTheRoutePlan() {
        UUID approverA = UUID.randomUUID();
        UUID approverB = UUID.randomUUID();
        Map<String, String> snapshotA = new LinkedHashMap<>();
        snapshotA.put("projectId", UUID.randomUUID().toString());
        snapshotA.put("role", "TEAM_LEADER");
        Map<String, String> snapshotB = new LinkedHashMap<>();
        snapshotB.put("departmentId", UUID.randomUUID().toString());
        snapshotB.put("role", "DEPT_HEAD");

        when(approvalRouteResolver.resolveRoutePlan(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(new ApprovalRouteResolver.ApprovalRoutePlan(List.of(
                new ApprovalRouteResolver.ApprovalRouteStep(approverA, 1, ApprovalContext.PROJECT, snapshotA),
                new ApprovalRouteResolver.ApprovalRouteStep(approverB, 2, ApprovalContext.DEPARTMENT, snapshotB)
            )));
        when(approvalStepFactory.buildSteps(workflowId, new ApprovalRouteResolver.ApprovalRoutePlan(List.of(
            new ApprovalRouteResolver.ApprovalRouteStep(approverA, 1, ApprovalContext.PROJECT, snapshotA),
            new ApprovalRouteResolver.ApprovalRouteStep(approverB, 2, ApprovalContext.DEPARTMENT, snapshotB)
        )))).thenReturn(List.of(
            ApprovalStep.builder()
                .workflowId(workflowId)
                .approverId(approverA)
                .stepOrder(1)
                .status(StepStatus.PENDING)
                .context(ApprovalContext.PROJECT)
                .routingSnapshot("{\"projectId\":\"" + snapshotA.get("projectId") + "\",\"role\":\"TEAM_LEADER\"}")
                .build(),
            ApprovalStep.builder()
                .workflowId(workflowId)
                .approverId(approverB)
                .stepOrder(2)
                .status(StepStatus.PENDING)
                .context(ApprovalContext.DEPARTMENT)
                .routingSnapshot("{\"departmentId\":\"" + snapshotB.get("departmentId") + "\",\"role\":\"DEPT_HEAD\"}")
                .build()
        ));
        when(approvalStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ApprovalStep> steps = approvalRouter.resolveSteps(
            requesterId, workflowId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

        assertThat(steps).hasSize(2);
        assertThat(steps).extracting(ApprovalStep::getWorkflowId).containsOnly(workflowId);
        assertThat(steps).extracting(ApprovalStep::getApproverId).containsExactly(approverA, approverB);
        assertThat(steps).extracting(ApprovalStep::getStepOrder).containsExactly(1, 2);
        assertThat(steps).extracting(ApprovalStep::getStatus).containsOnly(StepStatus.PENDING);
        assertThat(steps.getFirst().getRoutingSnapshot()).contains("TEAM_LEADER");
        assertThat(steps.get(1).getRoutingSnapshot()).contains("DEPT_HEAD");
        verify(approvalStepRepository).saveAll(steps);
    }

    @Test
    @DisplayName("resolveSteps preserves invalid empty route semantics")
    void resolveStepsPreservesInvalidEmptyRouteSemantics() {
        when(approvalRouteResolver.resolveRoutePlan(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(new ApprovalRouteResolver.ApprovalRoutePlan(List.of()));

        assertThatThrownBy(() -> approvalRouter.resolveSteps(
            requesterId, workflowId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .isInstanceOf(InvalidWorkflowStateException.class)
            .hasMessage("No approvers could be resolved for this workflow");
    }
}
