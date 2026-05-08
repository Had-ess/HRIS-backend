package com.hris.access.dto;

import java.util.List;

public record NavigationSectionDto(
    String code,
    String translationKey,
    List<NavigationItemDto> items
) {
}
