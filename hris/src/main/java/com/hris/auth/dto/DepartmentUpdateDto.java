package com.hris.auth.dto;

import java.util.UUID;

/**
 * PATCH payload for departments. name/code/isActive keep their null-means-keep
 * semantics, but headEmployeeId and parentDepartmentId are always applied:
 * the frontend form sends the full payload, so null clears the assignment.
 */
public record DepartmentUpdateDto(
    String name,
    String code,
    UUID headEmployeeId,
    UUID parentDepartmentId,
    Boolean isActive
) {}
