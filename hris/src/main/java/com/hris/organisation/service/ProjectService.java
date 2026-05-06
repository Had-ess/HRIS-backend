package com.hris.organisation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.User;
import com.hris.auth.entity.UserRole;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.RoleRepository;
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
import com.hris.organisation.dto.ProjectTeamCreateDto;
import com.hris.organisation.dto.ProjectTeamResponseDto;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.entity.ProjectDepartment;
import com.hris.organisation.entity.ProjectTeam;
import com.hris.organisation.mapper.ProjectMapper;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.organisation.repository.ProjectTeamRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ProjectDepartmentRepository projectDepartmentRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AccessScopeService accessScopeService;
    private final ProjectMapper projectMapper;
    private final AuditLogService auditLogService;
    private final TransactionalNotificationPublisher notificationPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<ProjectResponseDto> getAll(UUID userId, Pageable pageable) {
        ProjectReadScope scope = resolveReadScope(userId);
        return switch (scope.type()) {
            case ALL -> projectRepository.findAll(pageable).map(this::toProjectDto);
            case PROJECT -> scope.projectIds().isEmpty()
                ? Page.empty(pageable)
                : projectRepository.findByIdIn(scope.projectIds(), pageable).map(this::toProjectDto);
        };
    }

    @Transactional(readOnly = true)
    public ProjectResponseDto getById(UUID id, UUID userId) {
        Project project = getScopedProject(id, userId);
        return toProjectDto(project);
    }

    @Transactional
    public ProjectResponseDto create(ProjectCreateDto dto, UUID actorId) {
        validateProject(dto, null);
        Project project = projectMapper.toEntity(dto);
        project.setName(dto.name().trim());
        project.setCode(dto.code().trim());
        project.setProjectManagerEmployeeId(dto.projectManagerEmployeeId());
        Project saved = projectRepository.save(project);

        auditLogService.log(actorId, AuditAction.CREATE, "project",
            saved.getId(), null, saved);

        return toProjectDto(saved);
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
        project.setProjectManagerEmployeeId(dto.projectManagerEmployeeId());

        Project saved = projectRepository.save(project);
        auditLogService.log(actorId, AuditAction.UPDATE, "project",
            saved.getId(), previous, saved);

        return toProjectDto(saved);
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
            .teamId(null)
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

    @Transactional(readOnly = true)
    public List<ProjectTeamResponseDto> getTeams(UUID projectId, UUID userId) {
        getScopedProject(projectId, userId);

        return projectTeamRepository.findByProjectIdAndIsActiveTrue(projectId).stream()
            .map(this::toTeamDto)
            .toList();
    }

    @Transactional
    public ProjectTeamResponseDto createTeam(UUID projectId, ProjectTeamCreateDto dto, UUID actorId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        if (!projectDepartmentRepository.existsByProjectIdAndDepartmentId(projectId, dto.departmentId())) {
            throw new IllegalArgumentException("Department must be assigned to the project before creating a team");
        }

        Department department = departmentRepository.findById(dto.departmentId())
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));
        Employee supervisor = employeeRepository.findById(dto.supervisorEmployeeId())
            .orElseThrow(() -> new EntityNotFoundException("Supervisor not found"));

        validateTeam(dto, department, supervisor, projectId);

        ProjectTeam team = ProjectTeam.builder()
            .projectId(projectId)
            .departmentId(dto.departmentId())
            .name(dto.name().trim())
            .supervisorEmployeeId(dto.supervisorEmployeeId())
            .isActive(true)
            .build();
        ProjectTeam savedTeam = projectTeamRepository.save(team);
        auditLogService.log(actorId, AuditAction.CREATE, "project_team", savedTeam.getId(), null, savedTeam);

        createTeamAssignment(project, savedTeam, supervisor, supervisor, dto, actorId,
            com.hris.organisation.enums.ProjectRole.MANAGER);

        List<Employee> members = employeeRepository.findAllById(dto.employeeIds());
        for (Employee member : members) {
            createTeamAssignment(project, savedTeam, supervisor, member, dto, actorId,
                com.hris.organisation.enums.ProjectRole.MEMBER);
        }

        ensureProjectSupervisorRole(supervisor, actorId);
        return toTeamDto(savedTeam);
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

        validateProjectManager(dto.projectManagerEmployeeId());
    }

    private void validateProjectManager(UUID projectManagerEmployeeId) {
        if (projectManagerEmployeeId == null) {
            return;
        }

        Employee projectManager = employeeRepository.findById(projectManagerEmployeeId)
            .orElseThrow(() -> new EntityNotFoundException("Project manager not found"));
        if (projectManager.getStatus() != EmployeeStatus.ACTIVE) {
            throw new IllegalArgumentException("Project manager must be an active employee");
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
        List<UserRole> effectiveRoles = accessScopeService.getEffectiveRoles(userId);

        if (accessScopeService.hasAdministrationOrHrVisibility(effectiveRoles)) {
            return ProjectReadScope.all();
        }

        LinkedHashSet<UUID> projectIds = new LinkedHashSet<>();
        Employee employee = accessScopeService.findEmployee(userId).orElse(null);

        accessScopeService.resolveDepartmentManagerDepartmentId(effectiveRoles, employee)
            .ifPresent(departmentId ->
                projectIds.addAll(projectDepartmentRepository.findProjectIdsByDepartmentId(departmentId)));

        if (employee != null) {
            projectIds.addAll(projectRepository.findProjectIdsByProjectManagerEmployeeId(employee.getId()));
            projectIds.addAll(projectAssignmentRepository.findActiveProjectIdsByEmployeeId(
                employee.getId(), LocalDate.now()));

            if (accessScopeService.hasAnyRole(effectiveRoles, "PROJECT_SUPERVISOR")) {
                projectIds.addAll(projectAssignmentRepository.findActiveProjectIdsBySupervisorId(
                    employee.getId(), LocalDate.now()));
                projectIds.addAll(projectTeamRepository.findProjectIdsBySupervisorEmployeeId(employee.getId()));
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
            .projectManagerEmployeeId(project.getProjectManagerEmployeeId())
            .build();
    }

    private ProjectAssignment snapshot(ProjectAssignment assignment) {
        return ProjectAssignment.builder()
            .id(assignment.getId())
            .employeeId(assignment.getEmployeeId())
            .projectId(assignment.getProjectId())
            .teamId(assignment.getTeamId())
            .supervisorId(assignment.getSupervisorId())
            .assignmentRole(assignment.getAssignmentRole())
            .startDate(assignment.getStartDate())
            .endDate(assignment.getEndDate())
            .isActive(assignment.isActive())
            .build();
    }

    private void scheduleAssignmentNotification(Project project, Employee employee, Employee supervisor) {
        NotificationEvent event = buildAssignmentNotification(project, employee, supervisor);
        notificationPublisher.publishAfterCommit(event);
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
            dto.teamId(),
            dto.teamName(),
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

    private void validateTeam(
            ProjectTeamCreateDto dto,
            Department department,
            Employee supervisor,
            UUID projectId) {
        if (dto.endDate() != null && dto.startDate().isAfter(dto.endDate())) {
            throw new IllegalArgumentException("Team start date must be before or equal to end date");
        }
        if (!department.isActive()) {
            throw new IllegalArgumentException("Team department must be active");
        }
        if (supervisor.getStatus() != EmployeeStatus.ACTIVE) {
            throw new InvalidProjectAssignmentException("Supervisor must be an active employee");
        }
        if (!department.getId().equals(supervisor.getDepartmentId())) {
            throw new InvalidProjectAssignmentException("Supervisor must belong to the selected department");
        }
        long supervisorOverlappingAssignments = dto.endDate() != null
            ? projectAssignmentRepository.countOverlappingActiveAssignments(
                supervisor.getId(), projectId, dto.startDate(), dto.endDate())
            : projectAssignmentRepository.countOverlappingActiveAssignmentsOpenEnded(
                supervisor.getId(), projectId, dto.startDate());
        if (supervisorOverlappingAssignments > 0) {
            throw new InvalidProjectAssignmentException(
                "Overlapping assignment already exists for the selected team leader and project");
        }

        Set<UUID> projectDepartmentIds = projectDepartmentRepository.findByProjectId(projectId).stream()
            .map(ProjectDepartment::getDepartmentId)
            .collect(java.util.stream.Collectors.toSet());

        List<Employee> members = employeeRepository.findAllById(dto.employeeIds());
        if (members.size() != dto.employeeIds().size()) {
            throw new EntityNotFoundException("One or more employees were not found");
        }

        for (Employee member : members) {
            if (member.getStatus() != EmployeeStatus.ACTIVE) {
                throw new InvalidProjectAssignmentException("Team members must be active employees");
            }
            if (!projectDepartmentIds.contains(member.getDepartmentId())) {
                throw new InvalidProjectAssignmentException(
                    "Team members must belong to a department assigned to the project");
            }
            if (member.getId().equals(supervisor.getId())) {
                throw new InvalidProjectAssignmentException("Supervisor cannot also be selected as a team member");
            }
            long overlappingAssignments = dto.endDate() != null
                ? projectAssignmentRepository.countOverlappingActiveAssignments(
                    member.getId(), projectId, dto.startDate(), dto.endDate())
                : projectAssignmentRepository.countOverlappingActiveAssignmentsOpenEnded(
                    member.getId(), projectId, dto.startDate());
            if (overlappingAssignments > 0) {
                throw new InvalidProjectAssignmentException(
                    "Overlapping assignment already exists for this employee and project");
            }
        }
    }

    private void createTeamAssignment(
            Project project,
            ProjectTeam team,
            Employee supervisor,
            Employee member,
            ProjectTeamCreateDto dto,
            UUID actorId,
            com.hris.organisation.enums.ProjectRole assignmentRole) {
        ProjectAssignment assignment = ProjectAssignment.builder()
            .employeeId(member.getId())
            .projectId(project.getId())
            .teamId(team.getId())
            .supervisorId(supervisor.getId())
            .assignmentRole(assignmentRole)
            .startDate(dto.startDate())
            .endDate(dto.endDate())
            .isActive(true)
            .build();

        ProjectAssignment saved = projectAssignmentRepository.save(assignment);
        auditLogService.log(actorId, AuditAction.CREATE, "project_assignment", saved.getId(), null, saved);
        scheduleAssignmentNotification(project, member, supervisor);
    }

    private void ensureProjectSupervisorRole(Employee supervisor, UUID actorId) {
        Optional<Role> supervisorRole = roleRepository.findByCode("PROJECT_SUPERVISOR");
        if (supervisorRole.isEmpty()) {
            return;
        }
        Role role = supervisorRole.get();
        if (userRoleRepository.existsByUserIdAndRoleIdAndIsActiveTrue(supervisor.getUserId(), role.getId())) {
            return;
        }

        UserRole userRole = UserRole.builder()
            .userId(supervisor.getUserId())
            .roleId(role.getId())
            .isActive(true)
            .build();
        UserRole saved = userRoleRepository.save(userRole);
        auditLogService.log(actorId, AuditAction.CREATE, "user_role", saved.getId(), null, saved);
    }

    private ProjectTeamResponseDto toTeamDto(ProjectTeam team) {
        Department department = departmentRepository.findById(team.getDepartmentId())
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));
        Employee supervisor = employeeRepository.findById(team.getSupervisorEmployeeId())
            .orElseThrow(() -> new EntityNotFoundException("Supervisor not found"));
        String supervisorName = resolveEmployeeName(supervisor);

        return new ProjectTeamResponseDto(
            team.getId(),
            team.getProjectId(),
            department.getId(),
            department.getName(),
            department.getCode(),
            team.getName(),
            supervisor.getId(),
            supervisor.getEmployeeCode(),
            supervisorName,
            projectAssignmentRepository.countByTeamIdAndIsActiveTrue(team.getId()),
            team.isActive()
        );
    }

    private ProjectResponseDto toProjectDto(Project project) {
        Employee projectManager = project.getProjectManagerEmployeeId() == null
            ? null
            : employeeRepository.findById(project.getProjectManagerEmployeeId())
                .orElseThrow(() -> new EntityNotFoundException("Project manager not found"));

        return new ProjectResponseDto(
            project.getId(),
            project.getName(),
            project.getCode(),
            project.getStatus(),
            project.getStartDate(),
            project.getEndDate(),
            projectManager != null ? projectManager.getId() : null,
            projectManager != null ? projectManager.getEmployeeCode() : null,
            projectManager != null ? resolveEmployeeName(projectManager) : null
        );
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
