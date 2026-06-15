package com.hris.recruitment.service;

import com.hris.approval.enums.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RequisitionWorkflowCompletionHandlerTest {

    @Mock private RequisitionService requisitionService;

    @InjectMocks private RequisitionWorkflowCompletionHandler handler;

    @Test
    void supports_onlyRequisitionSubjectType() {
        assertThat(handler.supports("REQUISITION")).isTrue();
        assertThat(handler.supports("LEAVE")).isFalse();
    }

    @Test
    void handleCompletion_delegatesToService() {
        UUID subjectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        handler.handleCompletion(subjectId, WorkflowStatus.APPROVED, actorId);

        verify(requisitionService).handleWorkflowCompletion(subjectId, WorkflowStatus.APPROVED, actorId);
    }
}
