package com.hris.access.dto;

public record NavigationItemDto(
    String code,
    String translationKey,
    String sectionCode,
    String route,
    String icon,
    int displayOrder
) {
}
