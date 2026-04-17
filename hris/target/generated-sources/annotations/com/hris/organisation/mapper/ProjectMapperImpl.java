package com.hris.organisation.mapper;

import com.hris.organisation.dto.ProjectAssignmentResponseDto;
import com.hris.organisation.dto.ProjectCreateDto;
import com.hris.organisation.dto.ProjectResponseDto;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.enums.ProjectRole;
import com.hris.organisation.enums.ProjectStatus;
import java.time.LocalDate;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-17T23:27:05+0100",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.9 (Oracle Corporation)"
)
@Component
public class ProjectMapperImpl implements ProjectMapper {

    @Override
    public ProjectResponseDto toDto(Project project) {
        if ( project == null ) {
            return null;
        }

        UUID id = null;
        String name = null;
        String code = null;
        ProjectStatus status = null;
        LocalDate startDate = null;
        LocalDate endDate = null;

        id = project.getId();
        name = project.getName();
        code = project.getCode();
        status = project.getStatus();
        startDate = project.getStartDate();
        endDate = project.getEndDate();

        ProjectResponseDto projectResponseDto = new ProjectResponseDto( id, name, code, status, startDate, endDate );

        return projectResponseDto;
    }

    @Override
    public Project toEntity(ProjectCreateDto dto) {
        if ( dto == null ) {
            return null;
        }

        Project.ProjectBuilder project = Project.builder();

        project.name( dto.name() );
        project.code( dto.code() );
        project.status( dto.status() );
        project.startDate( dto.startDate() );
        project.endDate( dto.endDate() );

        return project.build();
    }

    @Override
    public ProjectAssignmentResponseDto toAssignmentDto(ProjectAssignment assignment) {
        if ( assignment == null ) {
            return null;
        }

        UUID id = null;
        UUID employeeId = null;
        UUID projectId = null;
        UUID supervisorId = null;
        ProjectRole assignmentRole = null;
        LocalDate startDate = null;
        LocalDate endDate = null;

        id = assignment.getId();
        employeeId = assignment.getEmployeeId();
        projectId = assignment.getProjectId();
        supervisorId = assignment.getSupervisorId();
        assignmentRole = assignment.getAssignmentRole();
        startDate = assignment.getStartDate();
        endDate = assignment.getEndDate();

        boolean isActive = false;

        ProjectAssignmentResponseDto projectAssignmentResponseDto = new ProjectAssignmentResponseDto( id, employeeId, projectId, supervisorId, assignmentRole, startDate, endDate, isActive );

        return projectAssignmentResponseDto;
    }
}
