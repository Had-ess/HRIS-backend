package com.hris.leave.service;

import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.service.WorkflowCompletionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LeaveWorkflowCompletionHandler implements WorkflowCompletionHandler {

    private static final String SUBJECT_TYPE = "LEAVE";

    private final LeaveRequestService leaveRequestService;

    @Override
    public boolean supports(String subjectType) {
        return SUBJECT_TYPE.equals(subjectType);
    }

    @Override
    public void handleCompletion(UUID subjectId, WorkflowStatus status, UUID actorId) {
        leaveRequestService.handleWorkflowCompletion(subjectId, status, actorId);
    }
}
