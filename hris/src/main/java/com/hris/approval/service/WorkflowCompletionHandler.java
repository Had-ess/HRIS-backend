package com.hris.approval.service;

import com.hris.approval.enums.WorkflowStatus;

import java.util.UUID;

public interface WorkflowCompletionHandler {
    boolean supports(String subjectType);

    void handleCompletion(UUID subjectId, WorkflowStatus status, UUID actorId);
}
