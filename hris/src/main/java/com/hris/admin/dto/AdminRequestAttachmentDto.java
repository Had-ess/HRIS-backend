package com.hris.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminRequestAttachmentDto(
    UUID id,
    String fileName,
    String contentType,
    long sizeBytes,
    boolean responseDocument,
    UUID uploadedByUserId,
    String uploadedByName,
    Instant uploadedAt
) {
}
