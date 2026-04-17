package com.hris.dashboard.dto;

import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;

import java.time.Instant;
import java.util.UUID;

public record ApprovalSummaryDto(
    UUID id,
    UUID workflowId,
    String subjectType,
    UUID subjectId,
    ApprovalContext context,
    int stepOrder,
    StepStatus status,
    Instant submittedAt
) {}
