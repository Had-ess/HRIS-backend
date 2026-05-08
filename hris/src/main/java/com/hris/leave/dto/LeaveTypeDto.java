package com.hris.leave.dto;

import java.util.UUID;

public record LeaveTypeDto(
    UUID id,
    String code,
    String name,
    boolean isPaid,
    boolean requiresJustification,
    boolean isActive,
    UUID validationWorkflowId,
    String validationWorkflowCode,
    String validationWorkflowName
) {
}
