package com.hris.approval.dto;

import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;

import java.time.Instant;
import java.util.UUID;

public record ApprovalStepResponseDto(
    UUID id,
    UUID workflowId,
    UUID approverId,
    int stepOrder,
    StepStatus status,
    ApprovalContext context,
    String comment,
    Instant decidedAt
) {}
