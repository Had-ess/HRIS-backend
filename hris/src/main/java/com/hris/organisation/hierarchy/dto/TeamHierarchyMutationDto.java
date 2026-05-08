package com.hris.organisation.hierarchy.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record TeamHierarchyMutationDto(
    @NotNull UUID teamId,
    @NotNull UUID collaboratorEmployeeId,
    UUID responsibleEmployeeId,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {
}
