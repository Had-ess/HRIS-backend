package com.hris.auth.dto;

import java.util.List;
import java.util.UUID;

public record OrgNodeDto(
    String id,
    String name,
    String code,
    long headcount,
    HeadEmployeeDto head,
    List<OrgNodeDto> children
) {
    public record HeadEmployeeDto(UUID id, String fullName, String jobTitle) {}
}
