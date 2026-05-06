package com.hris.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.approval.entity.ApprovalStep;
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
                step.routingSnapshot()
            ))
            .toList();
    }

    private ApprovalStep buildStep(UUID workflowId,
                                   UUID approverId,
                                   int order,
                                   com.hris.approval.enums.ApprovalContext context,
                                   Map<String, String> snapshot) {
        return ApprovalStep.builder()
            .workflowId(workflowId)
            .approverId(approverId)
            .stepOrder(order)
            .status(StepStatus.PENDING)
            .context(context)
            .routingSnapshot(serializeSnapshot(snapshot))
            .build();
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
