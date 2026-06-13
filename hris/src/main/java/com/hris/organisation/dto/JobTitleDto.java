package com.hris.organisation.dto;

import java.util.UUID;

public record JobTitleDto(
    UUID id,
    String name,
    String family,
    Integer level,
    boolean isActive,
    long employeeCount
) {}
