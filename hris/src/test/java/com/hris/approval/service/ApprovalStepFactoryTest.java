package com.hris.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalStepFactory Unit Tests")
class ApprovalStepFactoryTest {

    @Mock
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("buildSteps preserves workflow id approver id order and pending status")
    void buildStepsPreservesWorkflowIdApproverIdOrderAndPendingStatus() throws Exception {
        ApprovalStepFactory factory = new ApprovalStepFactory(objectMapper);
        UUID workflowId = UUID.randomUUID();
        UUID approverA = UUID.randomUUID();
        UUID approverB = UUID.randomUUID();
        Map<String, String> snapshotA = new LinkedHashMap<>();
        snapshotA.put("projectId", UUID.randomUUID().toString());
        snapshotA.put("role", "TEAM_LEADER");
        Map<String, String> snapshotB = new LinkedHashMap<>();
        snapshotB.put("departmentId", UUID.randomUUID().toString());
        snapshotB.put("role", "DEPT_HEAD");

        when(objectMapper.writeValueAsString(snapshotA)).thenReturn("{\"projectId\":\"" + snapshotA.get("projectId") + "\",\"role\":\"TEAM_LEADER\"}");
        when(objectMapper.writeValueAsString(snapshotB)).thenReturn("{\"departmentId\":\"" + snapshotB.get("departmentId") + "\",\"role\":\"DEPT_HEAD\"}");

        List<ApprovalStep> steps = factory.buildSteps(workflowId, new ApprovalRouteResolver.ApprovalRoutePlan(List.of(
            new ApprovalRouteResolver.ApprovalRouteStep(approverA, 1, ApprovalContext.PROJECT, snapshotA),
            new ApprovalRouteResolver.ApprovalRouteStep(approverB, 2, ApprovalContext.DEPARTMENT, snapshotB)
        )));

        assertThat(steps).hasSize(2);
        assertThat(steps).extracting(ApprovalStep::getWorkflowId).containsOnly(workflowId);
        assertThat(steps).extracting(ApprovalStep::getApproverId).containsExactly(approverA, approverB);
        assertThat(steps).extracting(ApprovalStep::getStepOrder).containsExactly(1, 2);
        assertThat(steps).extracting(ApprovalStep::getStatus).containsOnly(StepStatus.PENDING);
        assertThat(steps.getFirst().getRoutingSnapshot()).contains("TEAM_LEADER");
        assertThat(steps.get(1).getRoutingSnapshot()).contains("DEPT_HEAD");
    }

    @Test
    @DisplayName("buildSteps falls back to map toString when snapshot serialization fails")
    void buildStepsFallsBackToMapToStringWhenSnapshotSerializationFails() throws Exception {
        ApprovalStepFactory factory = new ApprovalStepFactory(objectMapper);
        UUID workflowId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("role", "ADMINISTRATION_FALLBACK");

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });

        List<ApprovalStep> steps = factory.buildSteps(workflowId, new ApprovalRouteResolver.ApprovalRoutePlan(List.of(
            new ApprovalRouteResolver.ApprovalRouteStep(approverId, 1, ApprovalContext.DEPARTMENT, snapshot)
        )));

        assertThat(steps).hasSize(1);
        assertThat(steps.getFirst().getRoutingSnapshot()).isEqualTo(snapshot.toString());
    }
}
