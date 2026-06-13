package com.hris.organisation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST/PATCH payload for the job catalog. The settings form sends the full
 * record, so family and level are full-apply (null clears); isActive defaults
 * to true on create and keeps its value when null on update.
 */
public record JobTitleCreateDto(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 100) String family,
    @Min(1) @Max(10) Integer level,
    Boolean isActive
) {}
