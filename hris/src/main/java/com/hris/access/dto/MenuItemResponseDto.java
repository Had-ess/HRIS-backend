package com.hris.access.dto;

import java.util.UUID;

public record MenuItemResponseDto(
    UUID id,
    String code,
    String translationKey,
    String sectionCode,
    String route,
    String icon,
    int displayOrder,
    boolean active
) {
}
