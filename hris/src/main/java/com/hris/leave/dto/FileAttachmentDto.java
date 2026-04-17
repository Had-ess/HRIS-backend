package com.hris.leave.dto;

import java.time.Instant;
import java.util.UUID;

public record FileAttachmentDto(
    UUID id,
    UUID requestId,
    String fileName,
    String mimeType,
    String storagePath,
    Instant uploadedAt
) {}
