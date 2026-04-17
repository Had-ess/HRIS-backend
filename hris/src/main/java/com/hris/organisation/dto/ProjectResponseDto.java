package com.hris.organisation.dto;

import com.hris.organisation.enums.ProjectStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ProjectResponseDto(
    UUID id,
    String name,
    String code,
    ProjectStatus status,
    LocalDate startDate,
    LocalDate endDate
) {}
