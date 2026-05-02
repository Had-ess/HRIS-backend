package com.hris.organisation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.entity.UserRole;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.common.exception.DuplicateProjectDepartmentAssignmentException;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidProjectAssignmentException;
import com.hris.organisation.dto.ProjectAssignmentCreateDto;
import com.hris.organisation.dto.ProjectAssignmentResponseDto;
import com.hris.organisation.dto.ProjectAssignmentViewDto;
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
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ProjectDepartmentRepository projectDepartmentRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ProjectMapper projectMapper;
    private final AuditLogService auditLogService;
    private final NotificationPublisher notificationPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<ProjectResponseDto> getAll(UUID userId, Pageable pageable) {
        ProjectReadScope scope = resolveReadScope(userId);
        return switch (scope.type()) {
            case ALL -> projectRepository.findAll(pageable).map(projectMapper::toDto);
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
        validateProject(dto, null);
        Project project = projectMapper.toEntity(dto);
        project.setName(dto.name().trim());
        project.setCode(dto.code().trim());
        Project saved = projectRepository.save(project);

        auditLogService.log(actorId, AuditAction.CREATE, "project",
            saved.getId(), null, saved);

        return projectMapper.toDto(saved);
    }

    @Transactional
    public ProjectResponseDto update(UUID projectId, ProjectCreateDto dto, UUID actorId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        Project previous = snapshot(project);

        validateProject(dto, projectId);

        project.setName(dto.name().trim());
        project.setCode(dto.code().trim());
        project.setStatus(dto.status());
        project.setStartDate(dto.startDate());
        project.setEndDate(dto.endDate());

        Project saved = projectRepository.save(project);
        auditLogService.log(actorId, AuditAction.UPDATE, "project",
            saved.getId(), previous, saved);

        return projectMapper.toDto(saved);
    }

    @Transactional
    public void deactivate(UUID projectId, UUID actorId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        Project previous = snapshot(project);

        project.setStatus(com.hris.organisation.enums.ProjectStatus.CANCELLED);
        if (project.getEndDate() == null || project.getEndDate().isAfter(LocalDate.now())) {
            project.setEndDate(LocalDate.now());
        }

        projectRepository.save(project);
        auditLogService.log(actorId, AuditAction.UPDATE, "project",
            project.getId(), previous, project);
    }

    @Transactional
    public ProjectAssignmentResponseDto assignEmployee(
            UUID projectId,
            ProjectAssignmentCreateDto dto,
            UUID actorId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        Employee employee = employeeRepository.findByIdForUpdate(dto.employeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        Employee supervisor = employeeRepository.findById(dto.supervisorId())
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

        scheduleAssignmentNotification(project, employee, supervisor);

        return projectMapper.toAssignmentDto(saved);
    }

    @Transactional
    public void removeAssignment(UUID projectId, UUID assignmentId, UUID actorId) {
        ProjectAssignment assignment = projectAssignmentRepository.findByIdForUpdate(assignmentId)
            .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));

        if (!assignment.getProjectId().equals(projectId)) {
            throw new IllegalStateException("Assignment does not belong to this project");
        }

        ProjectAssignment previous = snapshot(assignment);
        assignment.setActive(false);
        if (assignment.getEndDate() == null || assignment.getEndDate().isAfter(LocalDate.now())) {
            assignment.setEndDate(LocalDate.now());
        }
        projectAssignmentRepository.save(assignment);

        auditLogService.log(actorId, AuditAction.UPDATE, "project_assignment",
            assignmentId, previous, assignment);
    }

    @Transactional(readOnly = true)
    public List<ProjectAssignmentViewDto> getAssignments(UUID projectId, UUID userId) {
        getScopedProject(projectId, userId);
        return projectAssignmentRepository.findActiveViewsByProjectId(projectId).stream()
            .map(this::normalizeAssignmentView)
            .toList();
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

        long overlappingAssignments = dto.endDate() != null
            ? projectAssignmentRepository.countOverlappingActiveAssignments(
                dto.employeeId(), projectId, dto.startDate(), dto.endDate())
            : projectAssignmentRepository.countOverlappingActiveAssignmentsOpenEnded(
                dto.employeeId(), projectId, dto.startDate());
        if (overlappingAssignments > 0) {
            throw new InvalidProjectAssignmentException(
                "Overlapping assignment already exists for this employee and project");
        }
    }

    private void validateProject(ProjectCreateDto dto, UUID currentProjectId) {
        if (dto.endDate() != null && dto.startDate().isAfter(dto.endDate())) {
            throw new IllegalArgumentException(
                "Project start date must be before or equal to end date");
        }

        projectRepository.findByCode(dto.code().trim())
            .filter(existing -> currentProjectId == null || !existing.getId().equals(currentProjectId))
            .ifPresent(existing -> {
                throw new IllegalStateException("Project code must be unique");
            });
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

        LinkedHashSet<UUID> projectIds = new LinkedHashSet<>();
        Employee employee = employeeRepository.findByUserId(userId).orElse(null);

        if (hasRole(effectiveRoles, "DEPT_MANAGER")) {
            Optional<UUID> departmentId = effectiveRoles.stream()
                .filter(userRole -> userRole.getRole() != null && "DEPT_MANAGER".equals(userRole.getRole().getCode()))
                .map(UserRole::getDepartmentId)
                .filter(id -> id != null)
                .findFirst();
            if (departmentId.isEmpty() && employee != null) {
                departmentId = Optional.ofNullable(employee.getDepartmentId());
            }
            if (departmentId.isPresent()) {
                projectIds.addAll(projectDepartmentRepository.findProjectIdsByDepartmentId(departmentId.get()));
            }
        }

        if (employee != null) {
            projectIds.addAll(projectAssignmentRepository.findActiveProjectIdsByEmployeeId(
                employee.getId(), LocalDate.now()));

            if (hasRole(effectiveRoles, "PROJECT_SUPERVISOR")) {
                projectIds.addAll(projectAssignmentRepository.findActiveProjectIdsBySupervisorId(
                    employee.getId(), LocalDate.now()));
            }
        }

        return ProjectReadScope.projects(List.copyOf(projectIds));
    }

    private Project snapshot(Project project) {
        return Project.builder()
            .id(project.getId())
            .name(project.getName())
            .code(project.getCode())
            .status(project.getStatus())
            .startDate(project.getStartDate())
            .endDate(project.getEndDate())
            .build();
    }

    private ProjectAssignment snapshot(ProjectAssignment assignment) {
        return ProjectAssignment.builder()
            .id(assignment.getId())
            .employeeId(assignment.getEmployeeId())
            .projectId(assignment.getProjectId())
            .supervisorId(assignment.getSupervisorId())
            .assignmentRole(assignment.getAssignmentRole())
            .startDate(assignment.getStartDate())
            .endDate(assignment.getEndDate())
            .isActive(assignment.isActive())
            .build();
    }

    private void scheduleAssignmentNotification(Project project, Employee employee, Employee supervisor) {
        NotificationEvent event = buildAssignmentNotification(project, employee, supervisor);
        if (TransactionSynchronizationManager.isSynchronizationActive()
            && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationPublisher.publish(event);
                }
            });
            return;
        }
        notificationPublisher.publish(event);
    }

    private NotificationEvent buildAssignmentNotification(Project project, Employee employee, Employee supervisor) {
        User user = userRepository.findById(employee.getUserId())
            .orElseThrow(() -> new EntityNotFoundException("Assigned user not found"));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("projectName", project.getName());
        params.put("projectCode", project.getCode());
        params.put("supervisorName", resolveEmployeeName(supervisor));
        params.put("targetPath", "/projects/" + project.getId());

        return NotificationEvent.builder()
            .eventType(NotificationEventType.PROJECT_ASSIGNED)
            .targetUserId(user.getId())
            .titleKey("project.assigned.title")
            .bodyKey("project.assigned.body")
            .params(serializeMap(params))
            .locale(user.getLocalePreference())
            .routingKey("admin.project.assigned")
            .publishedAt(Instant.now())
            .build();
    }

    private String serializeMap(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize project notification params", ex);
        }
    }

    private String resolveEmployeeName(Employee employee) {
        return userRepository.findById(employee.getUserId())
            .map(user -> (user.getFirstName() + " " + user.getLastName()).trim())
            .filter(name -> !name.isBlank())
            .orElse(employee.getEmployeeCode());
    }

    private ProjectAssignmentViewDto normalizeAssignmentView(ProjectAssignmentViewDto dto) {
        return new ProjectAssignmentViewDto(
            dto.id(),
            dto.employeeId(),
            dto.employeeUserId(),
            dto.employeeCode(),
            dto.employeeName() == null ? dto.employeeCode() : dto.employeeName().trim(),
            dto.projectId(),
            dto.supervisorId(),
            dto.supervisorUserId(),
            dto.supervisorCode(),
            dto.supervisorName() == null ? dto.supervisorCode() : dto.supervisorName().trim(),
            dto.assignmentRole(),
            dto.startDate(),
            dto.endDate(),
            dto.isActive()
        );
    }

    private boolean hasRole(List<UserRole> roles, String roleCode) {
        return roles.stream().anyMatch(userRole ->
            userRole.getRole() != null && roleCode.equals(userRole.getRole().getCode()));
    }

    private record ProjectReadScope(ProjectReadScopeType type, List<UUID> projectIds) {
        static ProjectReadScope all() {
            return new ProjectReadScope(ProjectReadScopeType.ALL, List.of());
        }

        static ProjectReadScope projects(List<UUID> projectIds) {
            return new ProjectReadScope(ProjectReadScopeType.PROJECT, List.copyOf(projectIds));
        }
    }

    private enum ProjectReadScopeType {
        ALL,
        PROJECT
    }
}
