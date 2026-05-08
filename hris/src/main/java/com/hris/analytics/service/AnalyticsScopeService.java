package com.hris.analytics.service;

import com.hris.analytics.dto.AnalyticsScopeOptionDto;
import com.hris.analytics.enums.AnalyticsScopeType;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectRepository;
import com.hris.organisation.repository.TeamRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsScopeService {

    private final AccessScopeService accessScopeService;
    private final DepartmentRepository departmentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final TeamRepository teamRepository;

    @Transactional(readOnly = true)
    public List<AnalyticsScopeOptionDto> getAvailableScopes(UUID userId) {
        Employee employee = accessScopeService.getEmployeeOrThrow(userId);
        List<AnalyticsScopeOptionDto> scopes = new ArrayList<>();

        scopes.add(new AnalyticsScopeOptionDto(
            AnalyticsScopeType.EMPLOYEE,
            employee.getId(),
            "My analytics"
        ));

        if (accessScopeService.hasGlobalAnalyticsVisibility(userId)) {
            scopes.add(0, new AnalyticsScopeOptionDto(AnalyticsScopeType.GLOBAL, null, "Global"));
            return scopes;
        }

        accessScopeService.resolveDepartmentManagerDepartmentId(userId, employee)
            .flatMap(departmentRepository::findById)
            .ifPresent(department -> scopes.add(new AnalyticsScopeOptionDto(
                AnalyticsScopeType.DEPARTMENT,
                department.getId(),
                department.getName()
            )));

        if (accessScopeService.hasProjectScopedManagement(userId)) {
            Set<UUID> projectIds = new LinkedHashSet<>(projectAssignmentRepository.findActiveProjectIdsBySupervisorId(
                employee.getId(), LocalDate.now()));
            projectIds.addAll(teamRepository.findProjectIdsBySupervisorEmployeeId(employee.getId()));
            projectRepository.findAllById(projectIds).forEach(project -> scopes.add(
                new AnalyticsScopeOptionDto(AnalyticsScopeType.PROJECT, project.getId(), project.getName())
            ));

            for (var team : teamRepository.findBySupervisorEmployeeIdAndIsActiveTrue(employee.getId())) {
                scopes.add(new AnalyticsScopeOptionDto(AnalyticsScopeType.TEAM, team.getId(), team.getName()));
            }
        }

        return scopes;
    }

    @Transactional(readOnly = true)
    public void assertAccessible(UUID userId, AnalyticsScopeType scopeType, UUID scopeId) {
        boolean allowed = getAvailableScopes(userId).stream()
            .anyMatch(option -> option.scopeType() == scopeType
                && java.util.Objects.equals(option.scopeId(), scopeId));

        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException("Analytics scope is not accessible");
        }
    }
}
