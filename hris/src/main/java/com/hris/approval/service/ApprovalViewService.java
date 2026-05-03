package com.hris.approval.service;

import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalViewService {

    private static final String UNAVAILABLE_APPROVER = "Unavailable approver";

    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ApprovalStepResponseDto> getStepsForSubject(String subjectType, UUID subjectId) {
        if (subjectId == null) {
            return List.of();
        }

        return approvalWorkflowRepository.findBySubjectTypeAndSubjectId(subjectType, subjectId)
            .map(workflow -> mapStepsForWorkflows(
                List.of(workflow),
                approvalStepRepository.findByWorkflowIdOrderByStepOrder(workflow.getId())
            ).getOrDefault(subjectId, List.of()))
            .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<ApprovalStepResponseDto>> getStepsForSubjects(
            String subjectType,
            Collection<UUID> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return Map.of();
        }

        List<ApprovalWorkflow> workflows = approvalWorkflowRepository.findBySubjectTypeAndSubjectIdIn(
            subjectType,
            subjectIds.stream().distinct().toList()
        );
        if (workflows.isEmpty()) {
            return Map.of();
        }

        List<ApprovalStep> steps = approvalStepRepository.findByWorkflowIdInOrderByWorkflowIdAscStepOrderAsc(
            workflows.stream().map(ApprovalWorkflow::getId).toList()
        );
        return mapStepsForWorkflows(workflows, steps);
    }

    private Map<UUID, List<ApprovalStepResponseDto>> mapStepsForWorkflows(
            List<ApprovalWorkflow> workflows,
            List<ApprovalStep> steps) {
        Map<UUID, ApprovalWorkflow> workflowsById = workflows.stream()
            .collect(Collectors.toMap(ApprovalWorkflow::getId, Function.identity()));
        Map<UUID, String> approverNamesById = resolveApproverNames(steps);
        Map<UUID, List<ApprovalStepResponseDto>> stepsBySubjectId = new LinkedHashMap<>();

        for (ApprovalStep step : steps) {
            ApprovalWorkflow workflow = workflowsById.get(step.getWorkflowId());
            if (workflow == null) {
                continue;
            }

            stepsBySubjectId.computeIfAbsent(workflow.getSubjectId(), ignored -> new java.util.ArrayList<>())
                .add(new ApprovalStepResponseDto(
                    step.getId(),
                    step.getWorkflowId(),
                    workflow.getSubjectType(),
                    workflow.getSubjectId(),
                    null,
                    null,
                    null,
                    null,
                    step.getApproverId(),
                    approverNamesById.getOrDefault(step.getApproverId(), UNAVAILABLE_APPROVER),
                    step.getStepOrder(),
                    step.getStatus(),
                    step.getContext(),
                    step.getRoutingSnapshot(),
                    step.getComment(),
                    step.getDecidedAt()
                ));
        }

        for (ApprovalWorkflow workflow : workflows) {
            stepsBySubjectId.putIfAbsent(workflow.getSubjectId(), List.of());
        }

        return Collections.unmodifiableMap(stepsBySubjectId);
    }

    private Map<UUID, String> resolveApproverNames(List<ApprovalStep> steps) {
        Set<UUID> approverIds = steps.stream()
            .map(ApprovalStep::getApproverId)
            .collect(Collectors.toSet());
        if (approverIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(approverIds).stream()
            .collect(Collectors.toMap(User::getId, this::toDisplayName));
    }

    private String toDisplayName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }

        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        return email.isBlank() ? UNAVAILABLE_APPROVER : email;
    }
}
