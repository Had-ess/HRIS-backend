package com.hris.organisation.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.DuplicateProjectDepartmentAssignmentException;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidProjectAssignmentException;
import com.hris.organisation.dto.ProjectAssignmentCreateDto;
import com.hris.organisation.dto.ProjectAssignmentResponseDto;
import com.hris.organisation.dto.ProjectCreateDto;
import com.hris.organisation.dto.ProjectDepartmentAssignDto;
import com.hris.organisation.dto.ProjectDepartmentResponseDto;
import com.hris.organisation.dto.ProjectResponseDto;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.entity.ProjectDepartment;
import com.hris.organisation.mapper.ProjectMapper;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ProjectDepartmentRepository projectDepartmentRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectMapper projectMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<ProjectResponseDto> getAll(Pageable pageable) {
        return projectRepository.findAll(pageable).map(projectMapper::toDto);
    }

    @Transactional(readOnly = true)
    public ProjectResponseDto getById(UUID id) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        return projectMapper.toDto(project);
    }

    @Transactional
    public ProjectResponseDto create(ProjectCreateDto dto) {
        Project project = projectMapper.toEntity(dto);
        Project saved = projectRepository.save(project);

        auditLogService.log(null, AuditAction.CREATE, "project",
            saved.getId(), null, saved);

        return projectMapper.toDto(saved);
    }

    @Transactional
    public ProjectAssignmentResponseDto assignEmployee(UUID projectId,
                                                        ProjectAssignmentCreateDto dto) {
        projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        Employee employee = employeeRepository.findByIdForUpdate(dto.employeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        employeeRepository.findById(dto.supervisorId())
            .orElseThrow(() -> new EntityNotFoundException("Supervisor not found"));

        validateAssignment(dto, employee, projectId);

        ProjectAssignment assignment = ProjectAssignment.builder()
            .employeeId(dto.employeeId())
            .projectId(projectId)
            .supervisorId(dto.supervisorId())
            .assignmentRole(dto.assignmentRole())
            .startDate(dto.startDate())
            .endDate(dto.endDate())
            .isActive(true)
            .build();

        ProjectAssignment saved = projectAssignmentRepository.save(assignment);

        auditLogService.log(null, AuditAction.CREATE, "project_assignment",
            saved.getId(), null, saved);

        return projectMapper.toAssignmentDto(saved);
    }

    @Transactional
    public void removeAssignment(UUID projectId, UUID assignmentId) {
        ProjectAssignment assignment = projectAssignmentRepository.findByIdForUpdate(assignmentId)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));

        if (!assignment.getProjectId().equals(projectId)) {
            throw new IllegalStateException("Assignment does not belong to this project");
        }

        projectAssignmentRepository.delete(assignment);

        auditLogService.log(null, AuditAction.DELETE, "project_assignment",
            assignmentId, assignment, null);
    }

    @Transactional(readOnly = true)
    public List<ProjectDepartmentResponseDto> getDepartments(UUID projectId) {
        ensureProjectExists(projectId);

        return projectDepartmentRepository.findByProjectId(projectId).stream()
            .map(link -> {
                Department department = departmentRepository.findById(link.getDepartmentId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found"));
                return new ProjectDepartmentResponseDto(
                    link.getId(),
                    department.getId(),
                    department.getName(),
                    department.getCode(),
                    department.isActive(),
                    link.isLead()
                );
            })
            .toList();
    }

    @Transactional
    public ProjectDepartmentResponseDto assignDepartment(UUID projectId, ProjectDepartmentAssignDto dto) {
        ensureProjectExists(projectId);
        Department department = departmentRepository.findById(dto.departmentId())
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));

        if (projectDepartmentRepository.existsByProjectIdAndDepartmentId(projectId, dto.departmentId())) {
            throw new DuplicateProjectDepartmentAssignmentException(
                "Department is already assigned to this project");
        }

        ProjectDepartment link = ProjectDepartment.builder()
            .projectId(projectId)
            .departmentId(dto.departmentId())
            .isLead(Boolean.TRUE.equals(dto.isLead()))
            .build();

        ProjectDepartment saved = projectDepartmentRepository.save(link);
        auditLogService.log(null, AuditAction.CREATE, "project_department",
            saved.getId(), null, saved);

        return new ProjectDepartmentResponseDto(
            saved.getId(),
            department.getId(),
            department.getName(),
            department.getCode(),
            department.isActive(),
            saved.isLead()
        );
    }

    @Transactional
    public void removeDepartment(UUID projectId, UUID departmentId) {
        ensureProjectExists(projectId);

        projectDepartmentRepository.findByProjectIdAndDepartmentId(projectId, departmentId)
            .ifPresent(link -> {
                projectDepartmentRepository.delete(link);
                auditLogService.log(null, AuditAction.DELETE, "project_department",
                    link.getId(), link, null);
            });
    }

    private void validateAssignment(ProjectAssignmentCreateDto dto, Employee employee, UUID projectId) {
        if (dto.employeeId().equals(dto.supervisorId())) {
            throw new InvalidProjectAssignmentException("Supervisor cannot be the same employee");
        }

        if (dto.endDate() != null && dto.startDate().isAfter(dto.endDate())) {
            throw new InvalidProjectAssignmentException(
                "Assignment start date must be before or equal to end date");
        }

        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new InvalidProjectAssignmentException("Assignment cannot be created for inactive employee");
        }

        long overlappingAssignments = projectAssignmentRepository.countOverlappingActiveAssignments(
            dto.employeeId(), projectId, dto.startDate(), dto.endDate());
        if (overlappingAssignments > 0) {
            throw new InvalidProjectAssignmentException(
                "Overlapping assignment already exists for this employee and project");
        }
    }

    private void ensureProjectExists(UUID projectId) {
        projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found"));
    }
}
