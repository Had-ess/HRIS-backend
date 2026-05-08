package com.hris.access.dto;

import java.util.UUID;

public record UserProfileSummaryDto(
    UUID id,
    String code,
    String displayKey,
    boolean active
) {
}
