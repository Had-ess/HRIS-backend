package com.hris.approval.service;

import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.common.exception.InvalidWorkflowStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalRouter {

    private final ApprovalRouteResolver approvalRouteResolver;
    private final ApprovalStepFactory approvalStepFactory;
    private final ApprovalStepRepository approvalStepRepository;

    public List<ApprovalStep> resolveSteps(UUID requesterId, UUID workflowId,
                                            LocalDate startDate, LocalDate endDate) {
        ApprovalRouteResolver.ApprovalRoutePlan routePlan =
            approvalRouteResolver.resolveRoutePlan(requesterId, startDate, endDate);
        List<ApprovalStep> steps = approvalStepFactory.buildSteps(workflowId, routePlan);

        if (steps.isEmpty()) {
            throw new InvalidWorkflowStateException("No approvers could be resolved for this workflow");
        }

        return approvalStepRepository.saveAll(steps);
    }
}
