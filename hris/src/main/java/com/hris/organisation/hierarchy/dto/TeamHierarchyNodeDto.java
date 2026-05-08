package com.hris.organisation.hierarchy.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TeamHierarchyNodeDto(
    UUID id,
    UUID teamId,
    UUID collaboratorEmployeeId,
    String collaboratorEmployeeCode,
    String collaboratorName,
    UUID directResponsibleEmployeeId,
    String directResponsibleEmployeeCode,
    String directResponsibleName,
    String role,
    int level,
    long subordinateCount,
    String hierarchyStatus,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    Instant createdAt,
    Instant updatedAt
) {
}
