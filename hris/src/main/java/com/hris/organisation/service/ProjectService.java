package com.hris.organisation.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.UserRole;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRoleRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ProjectDepartmentRepository projectDepartmentRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRoleRepository userRoleRepository;
    private final ProjectMapper projectMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<ProjectResponseDto> getAll(UUID userId, Pageable pageable) {
        ProjectReadScope scope = resolveReadScope(userId);
        return switch (scope.type()) {
            case ALL -> projectRepository.findAll(pageable).map(projectMapper::toDto);
            case DEPARTMENT -> projectRepository.findByDepartmentId(scope.departmentId(), pageable)
                .map(projectMapper::toDto);
            case PROJECT -> scope.projectIds().isEmpty()
                ? Page.empty(pageable)
                : projectRepository.findByIdIn(scope.projectIds(), pageable).map(projectMapper::toDto);
        };
    }

    @Transactional(readOnly = true)
    public ProjectResponseDto getById(UUID id, UUID userId) {
        Project project = getScopedProject(id, userId);
        return projectMapper.toDto(project);
    }

    @Transactional
    public ProjectResponseDto create(ProjectCreateDto dto, UUID actorId) {
        Project project = projectMapper.toEntity(dto);
        Project saved = projectRepository.save(project);

        auditLogService.log(actorId, AuditAction.CREATE, "project",
            saved.getId(), null, saved);

        return projectMapper.toDto(saved);
    }

    @Transactional
    public ProjectAssignmentResponseDto assignEmployee(
            UUID projectId,
            ProjectAssignmentCreateDto dto,
            UUID actorId) {
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

        auditLogService.log(actorId, AuditAction.CREATE, "project_assignment",
            saved.getId(), null, saved);

        return projectMapper.toAssignmentDto(saved);
    }

    @Transactional
    public void removeAssignment(UUID projectId, UUID assignmentId, UUID actorId) {
        ProjectAssignment assignment = projectAssignmentRepository.findByIdForUpdate(assignmentId)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));

        if (!assignment.getProjectId().equals(projectId)) {
            throw new IllegalStateException("Assignment does not belong to this project");
        }

        projectAssignmentRepository.delete(assignment);

        auditLogService.log(actorId, AuditAction.DELETE, "project_assignment",
            assignmentId, assignment, null);
    }

    @Transactional(readOnly = true)
    public List<ProjectDepartmentResponseDto> getDepartments(UUID projectId, UUID userId) {
        getScopedProject(projectId, userId);

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
    public ProjectDepartmentResponseDto assignDepartment(
            UUID projectId,
            ProjectDepartmentAssignDto dto,
            UUID actorId) {
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
        auditLogService.log(actorId, AuditAction.CREATE, "project_department",
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
    public void removeDepartment(UUID projectId, UUID departmentId, UUID actorId) {
        ensureProjectExists(projectId);

        projectDepartmentRepository.findByProjectIdAndDepartmentId(projectId, departmentId)
            .ifPresent(link -> {
                projectDepartmentRepository.delete(link);
                auditLogService.log(actorId, AuditAction.DELETE, "project_department",
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

    private Project getScopedProject(UUID projectId, UUID userId) {
        ProjectReadScope scope = resolveReadScope(userId);

        return switch (scope.type()) {
            case ALL -> projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
            case DEPARTMENT -> projectRepository.findScopedByDepartmentId(projectId, scope.departmentId())
                .orElseThrow(() -> new AccessDeniedException("You are not allowed to access this project"));
            case PROJECT -> {
                if (scope.projectIds().isEmpty()) {
                    throw new AccessDeniedException("You are not allowed to access this project");
                }
                yield projectRepository.findScopedByProjectIds(projectId, scope.projectIds())
                    .orElseThrow(() -> new AccessDeniedException("You are not allowed to access this project"));
            }
        };
    }

    private ProjectReadScope resolveReadScope(UUID userId) {
        List<UserRole> effectiveRoles = userRoleRepository.findEffectiveByUserId(userId, Instant.now()).stream()
            .filter(userRole -> userRole.getRole() != null && userRole.getRole().isActive())
            .toList();

        if (hasRole(effectiveRoles, "ADMINISTRATION") || hasRole(effectiveRoles, "HR_ADMIN")) {
            return ProjectReadScope.all();
        }

        if (hasRole(effectiveRoles, "DEPT_MANAGER")) {
            UUID departmentId = effectiveRoles.stream()
                .filter(userRole -> userRole.getRole() != null && "DEPT_MANAGER".equals(userRole.getRole().getCode()))
                .map(UserRole::getDepartmentId)
                .filter(id -> id != null)
                .findFirst()
                .orElseGet(() -> employeeRepository.findByUserId(userId)
                    .map(Employee::getDepartmentId)
                    .orElseThrow(() -> new EntityNotFoundException("Employee not found")));
            return ProjectReadScope.department(departmentId);
        }

        if (hasRole(effectiveRoles, "PROJECT_SUPERVISOR")) {
            Employee employee = employeeRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
            List<UUID> projectIds = projectAssignmentRepository.findActiveProjectIdsBySupervisorId(
                employee.getId(), LocalDate.now());
            return ProjectReadScope.projects(projectIds);
        }

        throw new AccessDeniedException("You are not allowed to access projects");
    }

    private boolean hasRole(List<UserRole> roles, String roleCode) {
        return roles.stream().anyMatch(userRole ->
            userRole.getRole() != null && roleCode.equals(userRole.getRole().getCode()));
    }

    private record ProjectReadScope(ProjectReadScopeType type, UUID departmentId, List<UUID> projectIds) {
        static ProjectReadScope all() {
            return new ProjectReadScope(ProjectReadScopeType.ALL, null, List.of());
        }

        static ProjectReadScope department(UUID departmentId) {
            return new ProjectReadScope(ProjectReadScopeType.DEPARTMENT, departmentId, List.of());
        }

        static ProjectReadScope projects(List<UUID> projectIds) {
            return new ProjectReadScope(ProjectReadScopeType.PROJECT, null, List.copyOf(projectIds));
        }
    }

    private enum ProjectReadScopeType {
        ALL,
        DEPARTMENT,
        PROJECT
    }
}
