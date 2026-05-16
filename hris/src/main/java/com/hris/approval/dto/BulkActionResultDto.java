package com.hris.approval.dto;

import java.util.List;
import java.util.UUID;

public record BulkActionResultDto(int succeeded, int failed, List<BulkError> errors) {
    public record BulkError(UUID id, String message) {}
}
