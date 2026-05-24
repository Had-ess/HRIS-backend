package com.hris.organisation.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.access.service.AccessResolutionService;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.PageResponse;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.organisation.dto.TeamCreateDto;
import com.hris.organisation.dto.TeamDto;
import com.hris.organisation.dto.TeamUpdateDto;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.Team;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.organisation.repository.TeamRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final ProjectRepository projectRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AccessScopeService accessScopeService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<TeamDto> getAll(UUID actorId, Pageable pageable) {
        TeamReadScope scope = resolveReadScope(actorId);
        return switch (scope.type()) {
            case GLOBAL -> PageResponse.of(teamRepository.findAllByOrderByNameAsc(pageable).map(this::toDto));
            case DEPARTMENT -> PageResponse.of(teamRepository.findByDepartmentIdOrderByNameAsc(scope.departmentId(), pageable).map(this::toDto));
            case DEPARTMENTS -> PageResponse.of(teamRepository.findByDepartmentIdInOrderByNameAsc(scope.departmentIds(), pageable).map(this::toDto));
            case SELF -> PageResponse.of(org.springframework.data.domain.Page.empty(pageable));
        };
    }

    @Transactional(readOnly = true)
    public TeamDto getById(UUID id, UUID actorId) {
        TeamReadScope scope = resolveReadScope(actorId);
        Team team = getEntity(id);
        if (scope.type() == TeamReadScopeType.DEPARTMENT && !scope.departmentId().equals(team.getDepartmentId())) {
            throw new AccessDeniedException("You are not allowed to access teams");
        }
        if (scope.type() == TeamReadScopeType.DEPARTMENTS && !scope.departmentIds().contains(team.getDepartmentId())) {
            throw new AccessDeniedException("You are not allowed to access teams");
        }
        if (scope.type() == TeamReadScopeType.SELF) {
            throw new AccessDeniedException("You are not allowed to access teams");
        }
        return toDto(team);
    }

    @Transactional
    public TeamDto create(TeamCreateDto dto, UUID actorId) {
        validate(dto.code(), dto.name(), dto.departmentId(), dto.projectId(), dto.supervisorEmployeeId(), null);
        Team team = Team.builder()
            .id(UUID.randomUUID())
            .code(normalizeCode(dto.code()))
            .name(dto.name().trim())
            .departmentId(dto.departmentId())
            .projectId(dto.projectId())
            .supervisorEmployeeId(dto.supervisorEmployeeId())
            .isActive(true)
            .build();
        Team saved = teamRepository.save(team);
        auditLogService.log(actorId, AuditAction.CREATE, "team", saved.getId(), null, snapshot(saved));
        return toDto(saved);
    }

    @Transactional
    public TeamDto update(UUID id, TeamUpdateDto dto, UUID actorId) {
        Team existing = getEntity(id);
        String nextCode = dto.code() != null ? dto.code() : existing.getCode();
        String nextName = dto.name() != null ? dto.name() : existing.getName();
        UUID nextDepartmentId = dto.departmentId() != null ? dto.departmentId() : existing.getDepartmentId();
        UUID nextProjectId = dto.projectId() != null ? dto.projectId() : existing.getProjectId();
        UUID nextSupervisorEmployeeId = dto.supervisorEmployeeId() != null ? dto.supervisorEmployeeId() : existing.getSupervisorEmployeeId();

        validate(nextCode, nextName, nextDepartmentId, nextProjectId, nextSupervisorEmployeeId, id);
        Map<String, Object> previous = snapshot(existing);
        if (dto.code() != null) {
            existing.setCode(normalizeCode(dto.code()));
        }
        if (dto.name() != null) {
            existing.setName(dto.name().trim());
        }
        if (dto.departmentId() != null) {
            existing.setDepartmentId(dto.departmentId());
        }
        if (dto.projectId() != null) {
            existing.setProjectId(dto.projectId());
        }
        if (dto.supervisorEmployeeId() != null) {
            existing.setSupervisorEmployeeId(dto.supervisorEmployeeId());
        }
        if (dto.active() != null) {
            existing.setActive(dto.active());
        }
        Team saved = teamRepository.save(existing);
        auditLogService.log(actorId, AuditAction.UPDATE, "team", saved.getId(), previous, snapshot(saved));
        return toDto(saved);
    }

    @Transactional
    public void deactivate(UUID id, UUID actorId) {
        Team team = getEntity(id);
        Map<String, Object> previous = snapshot(team);
        team.setActive(false);
        teamRepository.save(team);
        auditLogService.log(actorId, AuditAction.UPDATE, "team", team.getId(), previous, snapshot(team));
    }

    private void validate(
            String code,
            String name,
            UUID departmentId,
            UUID projectId,
            UUID supervisorEmployeeId,
            UUID currentId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Team code is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Team name is required");
        }
        boolean duplicateCode = currentId == null
            ? teamRepository.existsByCodeIgnoreCase(code.trim())
            : teamRepository.existsByCodeIgnoreCaseAndIdNot(code.trim(), currentId);
        if (duplicateCode) {
            throw new IllegalStateException("Team code must be unique");
        }

        if (projectId == null) {
            throw new IllegalArgumentException("Team project is required");
        }
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        Department department = departmentRepository.findById(departmentId)
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));
        if (!department.isActive()) {
            throw new IllegalArgumentException("Team department must be active");
        }

        Employee supervisor = employeeRepository.findById(supervisorEmployeeId)
            .orElseThrow(() -> new EntityNotFoundException("Supervisor not found"));
        if (supervisor.getStatus() != EmployeeStatus.ACTIVE) {
            throw new IllegalArgumentException("Team supervisor must be active");
        }
    }

    private Team getEntity(UUID id) {
        return teamRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Team not found"));
    }

    private TeamDto toDto(Team team) {
        Department department = departmentRepository.findById(team.getDepartmentId())
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));
        Employee supervisor = employeeRepository.findById(team.getSupervisorEmployeeId())
            .orElseThrow(() -> new EntityNotFoundException("Supervisor not found"));
        Project project = projectRepository.findById(team.getProjectId())
            .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        return new TeamDto(
            team.getId(),
            team.getCode(),
            team.getName(),
            department.getId(),
            department.getName(),
            department.getCode(),
            project.getId(),
            project.getName(),
            project.getCode(),
            supervisor.getId(),
            supervisor.getEmployeeCode(),
            resolveEmployeeName(supervisor),
            team.isActive()
        );
    }

    private String resolveEmployeeName(Employee employee) {
        User user = userRepository.findById(employee.getUserId())
            .orElseThrow(() -> new EntityNotFoundException("Employee user not found"));
        String name = (user.getFirstName() + " " + user.getLastName()).trim();
        return name.isBlank() ? employee.getEmployeeCode() : name;
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> snapshot(Team team) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", team.getCode());
        state.put("name", team.getName());
        state.put("departmentId", team.getDepartmentId());
        state.put("projectId", team.getProjectId());
        state.put("supervisorEmployeeId", team.getSupervisorEmployeeId());
        state.put("active", team.isActive());
        return state;
    }

    private TeamReadScope resolveReadScope(UUID actorId) {
        AccessResolutionService.ScopeResolution scope = accessScopeService.resolveDepartmentDataScope(actorId);
        if (scope.isGlobal()) {
            return TeamReadScope.global();
        }
        if (scope.isDepartment() && !scope.departmentIds().isEmpty()) {
            if (scope.departmentIds().size() == 1) {
                return TeamReadScope.department(scope.departmentIds().get(0));
            }
            return TeamReadScope.departments(scope.departmentIds());
        }
        return TeamReadScope.self();
    }

    private record TeamReadScope(TeamReadScopeType type, UUID departmentId, List<UUID> departmentIds) {
        static TeamReadScope global() {
            return new TeamReadScope(TeamReadScopeType.GLOBAL, null, List.of());
        }

        static TeamReadScope department(UUID departmentId) {
            return new TeamReadScope(TeamReadScopeType.DEPARTMENT, departmentId, List.of(departmentId));
        }

        static TeamReadScope departments(List<UUID> departmentIds) {
            return new TeamReadScope(TeamReadScopeType.DEPARTMENTS, null, List.copyOf(departmentIds));
        }

        static TeamReadScope self() {
            return new TeamReadScope(TeamReadScopeType.SELF, null, List.of());
        }
    }

    private enum TeamReadScopeType {
        GLOBAL,
        DEPARTMENT,
        DEPARTMENTS,
        SELF
    }
}
