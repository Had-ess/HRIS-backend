package com.hris.recruitment.service;

import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.service.WorkflowCompletionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Plugs requisition approval into the shared approval engine: on workflow completion the
 * engine dispatches here, flipping the requisition OPEN (approved) or back to DRAFT (rejected).
 */
@Component
@RequiredArgsConstructor
public class RequisitionWorkflowCompletionHandler implements WorkflowCompletionHandler {

    private final RequisitionService requisitionService;

    @Override
    public boolean supports(String subjectType) {
        return RequisitionApprovalWorkflowService.SUBJECT_TYPE.equals(subjectType);
    }

    @Override
    public void handleCompletion(UUID subjectId, WorkflowStatus status, UUID actorId) {
        requisitionService.handleWorkflowCompletion(subjectId, status, actorId);
    }
}
