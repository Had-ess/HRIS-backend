package com.hris.approval.dto;

import com.hris.approval.enums.WorkflowStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApprovalWorkflowResponseDto(
    UUID id,
    String subjectType,
    UUID subjectId,
    WorkflowStatus status,
    Instant createdAt,
    Instant completedAt,
    List<ApprovalStepResponseDto> steps
) {}
