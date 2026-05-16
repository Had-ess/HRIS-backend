package com.hris.approval.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkApproveRequestDto(
    @NotEmpty @Size(max = 50) List<UUID> approvalIds,
    String comment
) {}
