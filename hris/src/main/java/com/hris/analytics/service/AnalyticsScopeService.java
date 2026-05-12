package com.hris.analytics.service;

import com.hris.access.service.AccessResolutionService;
import com.hris.analytics.dto.AnalyticsFilterOptionDto;
import com.hris.analytics.dto.AnalyticsFiltersDto;
import com.hris.analytics.dto.AnalyticsScopeOptionDto;
import com.hris.analytics.enums.AnalyticsScopeType;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.organisation.entity.Team;
import com.hris.organisation.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsScopeService {

    private final AccessResolutionService accessResolutionService;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AdminRequestTypeRepository adminRequestTypeRepository;

    @Transactional(readOnly = true)
    public List<AnalyticsScopeOptionDto> getAvailableScopes(UUID userId) {
        Set<String> permissions = accessResolutionService.getEffectivePermissionNames(userId);
        Employee employee = employeeRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        Map<String, AnalyticsScopeOptionDto> scopes = new LinkedHashMap<>();
        if (permissions.contains("ANALYTICS_READ_OWN")) {
            addScope(scopes, new AnalyticsScopeOptionDto(AnalyticsScopeType.EMPLOYEE, employee.getId(), "My analytics"));
        }

        if (permissions.contains("ANALYTICS_READ_SCOPED") || permissions.contains("ANALYTICS_READ_GLOBAL")) {
            for (Department department : departmentRepository.findByHeadEmployeeIdAndIsActiveTrue(employee.getId())) {
                addScope(scopes, new AnalyticsScopeOptionDto(AnalyticsScopeType.DEPARTMENT, department.getId(), department.getName()));
            }
            for (Team team : teamRepository.findBySupervisorEmployeeIdAndIsActiveTrue(employee.getId())) {
                addScope(scopes, new AnalyticsScopeOptionDto(AnalyticsScopeType.TEAM, team.getId(), team.getName()));
            }
        }

        if (permissions.contains("ANALYTICS_READ_GLOBAL")) {
            addScope(scopes, new AnalyticsScopeOptionDto(AnalyticsScopeType.GLOBAL, null, "Global"));
        }

        return scopes.values().stream()
            .sorted(Comparator
                .comparing((AnalyticsScopeOptionDto option) -> option.scopeType() != AnalyticsScopeType.GLOBAL)
                .thenComparing(AnalyticsScopeOptionDto::label))
            .toList();
    }

    @Transactional(readOnly = true)
    public AnalyticsFiltersDto getFilters(UUID userId) {
        List<AnalyticsScopeOptionDto> scopes = getAvailableScopes(userId);
        boolean hasGlobal = scopes.stream().anyMatch(scope -> scope.scopeType() == AnalyticsScopeType.GLOBAL);

        List<AnalyticsFilterOptionDto> departments = hasGlobal
            ? departmentRepository.findAll().stream()
                .filter(Department::isActive)
                .sorted(Comparator.comparing(Department::getName))
                .map(department -> new AnalyticsFilterOptionDto(department.getId(), department.getCode(), department.getName()))
                .toList()
            : List.of();

        List<AnalyticsFilterOptionDto> teams = hasGlobal
            ? teamRepository.findAll().stream()
                .filter(Team::isActive)
                .sorted(Comparator.comparing(Team::getName))
                .map(team -> new AnalyticsFilterOptionDto(team.getId(), team.getCode(), team.getName()))
                .toList()
            : new ArrayList<>(scopes.stream()
                .filter(scope -> scope.scopeType() == AnalyticsScopeType.TEAM)
                .map(scope -> new AnalyticsFilterOptionDto(scope.scopeId(), null, scope.label()))
                .toList());

        List<AnalyticsFilterOptionDto> leaveTypes = leaveTypeRepository.findAll().stream()
            .filter(type -> type.isActive() || !type.isBalanceTracked())
            .sorted(Comparator.comparing(type -> type.getName().toLowerCase(java.util.Locale.ROOT)))
            .map(type -> new AnalyticsFilterOptionDto(type.getId(), type.getCode(), type.getName()))
            .toList();

        List<AnalyticsFilterOptionDto> adminRequestTypes = adminRequestTypeRepository.findAll().stream()
            .filter(type -> type.isActive())
            .sorted(Comparator.comparing(type -> type.getName().toLowerCase(java.util.Locale.ROOT)))
            .map(type -> new AnalyticsFilterOptionDto(type.getId(), type.getCode(), type.getName()))
            .toList();

        return new AnalyticsFiltersDto(scopes, departments, teams, leaveTypes, adminRequestTypes);
    }

    @Transactional(readOnly = true)
    public AnalyticsScopeOptionDto getDefaultScope(UUID userId) {
        return getAvailableScopes(userId).stream()
            .findFirst()
            .orElseThrow(() -> new AccessDeniedException("No analytics scope is available"));
    }

    @Transactional(readOnly = true)
    public void assertAccessible(UUID userId, AnalyticsScopeType scopeType, UUID scopeId) {
        boolean allowed = getAvailableScopes(userId).stream()
            .anyMatch(option -> option.scopeType() == scopeType
                && java.util.Objects.equals(option.scopeId(), scopeId));
        if (!allowed) {
            throw new AccessDeniedException("Analytics scope is not accessible");
        }
    }

    private void addScope(Map<String, AnalyticsScopeOptionDto> scopes, AnalyticsScopeOptionDto option) {
        String key = option.scopeType() + ":" + (option.scopeId() == null ? "global" : option.scopeId());
        scopes.putIfAbsent(key, option);
    }
}
