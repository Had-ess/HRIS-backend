package com.hris.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.ApprovalSourceType;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalStepFactory {

    private final ObjectMapper objectMapper;

    public List<ApprovalStep> buildSteps(UUID workflowId, ApprovalRouteResolver.ApprovalRoutePlan routePlan) {
        return routePlan.steps().stream()
            .map(step -> buildStep(
                workflowId,
                step.approverId(),
                step.stepOrder(),
                step.context(),
                step.routingSnapshot(),
                resolveSourceType(step.context(), step.routingSnapshot()),
                resolveApproverLevel(step.routingSnapshot())
            ))
            .toList();
    }

    private ApprovalStep buildStep(UUID workflowId,
                                   UUID approverId,
                                   int order,
                                   ApprovalContext context,
                                   Map<String, String> snapshot,
                                   ApprovalSourceType sourceType,
                                   int approverLevel) {
        return ApprovalStep.builder()
            .workflowId(workflowId)
            .approverId(approverId)
            .stepOrder(order)
            .status(StepStatus.PENDING)
            .context(context)
            .sourceType(sourceType)
            .approverLevel(approverLevel)
            .routingSnapshot(serializeSnapshot(snapshot))
            .build();
    }

    private ApprovalSourceType resolveSourceType(ApprovalContext context, Map<String, String> snapshot) {
        if (context == ApprovalContext.DEPARTMENT) {
            return ApprovalSourceType.PRIMARY_CHAIN;
        }
        if (snapshot != null && snapshot.containsKey("teamId")) {
            return ApprovalSourceType.TEAM_CHAIN;
        }
        return ApprovalSourceType.PROJECT_CHAIN;
    }

    private int resolveApproverLevel(Map<String, String> snapshot) {
        if (snapshot == null) {
            return 1;
        }
        try {
            return Integer.parseInt(snapshot.getOrDefault("distance", "1"));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private String serializeSnapshot(Map<String, String> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize routing snapshot", e);
            return snapshot.toString();
        }
    }
}
