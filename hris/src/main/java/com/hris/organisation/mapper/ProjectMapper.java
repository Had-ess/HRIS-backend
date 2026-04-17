package com.hris.organisation.mapper;

import com.hris.organisation.dto.ProjectCreateDto;
import com.hris.organisation.dto.ProjectResponseDto;
import com.hris.organisation.dto.ProjectAssignmentResponseDto;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.ProjectAssignment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponseDto toDto(Project project);

    Project toEntity(ProjectCreateDto dto);

    ProjectAssignmentResponseDto toAssignmentDto(ProjectAssignment assignment);
}
