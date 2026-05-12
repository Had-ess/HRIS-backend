package com.hris.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminRequestCommentDto(
    UUID id,
    UUID authorUserId,
    String authorName,
    String comment,
    boolean internal,
    Instant createdAt
) {
}
