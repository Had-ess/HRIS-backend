package com.hris.organisation.dto;

import java.util.List;
import java.util.UUID;

/** Read models of the org chart endpoints (ORG_BACKBONE_DESIGN.md §7). */
public final class OrgChartDtos {

    private OrgChartDtos() {
    }

    /** One node of the department tree; roots are departments without a parent. */
    public record DepartmentNode(
        UUID id,
        String name,
        String code,
        UUID headEmployeeId,
        String headName,
        long employeeCount,
        List<TeamNode> teams,
        List<DepartmentNode> children
    ) {
    }

    public record TeamNode(
        UUID id,
        String name,
        String supervisorName
    ) {
    }

    /** One node of the supervisor forest; roots are employees without a supervisor. */
    public record SpineNode(
        UUID employeeId,
        String name,
        String jobTitle,
        String departmentName,
        List<SpineNode> children
    ) {
    }
}
