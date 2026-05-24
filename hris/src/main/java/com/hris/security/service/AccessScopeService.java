package com.hris.security.service;

import com.hris.access.service.AccessResolutionService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectDepartmentRepository;
import com.hris.organisation.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessScopeService {

    /**
     * Profiles that grant unrestricted (global) visibility regardless of assignment source.
     * Any other manual profile (e.g. MANAGER_INBOX) is treated as department-scoped to the
     * user's own department — managers see their department only, not the whole company.
     */
    private static final Set<String> GLOBAL_SCOPE_PROFILE_CODES = Set.of("HR_CONSOLE", "ADMIN_CONSOLE");

    private static final String[] BUSINESS_DATA_PERMISSIONS = {
        "EMPLOYEE_READ",
        "EMPLOYEE_MANAGE",
        "DEPARTMENT_READ",
        "DEPARTMENT_MANAGE",
        "TEAM_READ",
        "TEAM_MANAGE",
        "PROJECT_ASSIGNMENT_MANAGE",
        "PROJECT_PORTFOLIO_MANAGE",
        "LEAVE_REQUEST_READ",
        "LEAVE_BALANCE_READ_SCOPED",
        "LEAVE_BALANCE_MANAGE",
        "ADMIN_REQUEST_READ_GLOBAL",
        "ANALYTICS_READ",
        "REPORT_READ",
        "AUDIT_LOG_READ"
    };

    private final AccessResolutionService accessResolutionService;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final TeamHierarchyRelationRepository teamHierarchyRelationRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ProjectDepartmentRepository projectDepartmentRepository;

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, String resource, String action) {
        return accessResolutionService.hasPermission(userId, resource, action);
    }

    @Transactional(readOnly = true)
    public boolean hasPermissionName(UUID userId, String permissionName) {
        return accessResolutionService.hasPermissionName(userId, permissionName);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyPermissionName(UUID userId, String... permissionNames) {
        for (String permissionName : permissionNames) {
            if (hasPermissionName(userId, permissionName)) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public Optional<Employee> findEmployee(UUID userId) {
        return employeeRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Employee getEmployeeOrThrow(UUID userId) {
        return findEmployee(userId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    }

    public Optional<UUID> resolveDepartmentManagerDepartmentId(UUID userId, Employee employee) {
        AccessResolutionService.ScopeResolution scope = resolveDepartmentDataScope(userId);
        if (scope.isDepartment() && !scope.departmentIds().isEmpty()) {
            return Optional.of(scope.departmentIds().get(0));
        }
        if (scope.isSelf()) {
            return Optional.ofNullable(employee).map(Employee::getDepartmentId);
        }
        return Optional.empty();
    }

    /**
     * Resolves the approval-data breadth attached to the user's auto-granted
     * {@code DEPT_APPROVER_PROFILE} assignments. See
     * {@link AccessResolutionService#resolveApprovalScope(UUID)} for the precedence rules.
     */
    @Transactional(readOnly = true)
    public AccessResolutionService.ScopeResolution resolveApprovalScope(UUID userId) {
        return accessResolutionService.resolveApprovalScope(userId);
    }

    @Transactional(readOnly = true)
    public AccessResolutionService.ScopeResolution resolveDepartmentDataScope(UUID userId) {
        if (hasGlobalScopeProfile(userId)) {
            return AccessResolutionService.ScopeResolution.global();
        }

        Set<UUID> departmentIds = new LinkedHashSet<>();

        // SYSTEM-sourced department refs (e.g. DEPT_APPROVER_PROFILE for the department head).
        departmentIds.addAll(accessResolutionService.resolveSystemSourcedDepartmentIds(userId));

        // Manager-level visibility (e.g. MANAGER_INBOX granted manually) does NOT promote
        // to global — instead, scope to the user's own department.
        Employee employee = findEmployee(userId).orElse(null);
        if (employee != null && employee.getDepartmentId() != null && hasManagerDepartmentVisibility(userId)) {
            departmentIds.add(employee.getDepartmentId());
        }

        if (!departmentIds.isEmpty()) {
            return AccessResolutionService.ScopeResolution.department(List.copyOf(departmentIds));
        }

        return AccessResolutionService.ScopeResolution.self();
    }

    private boolean hasGlobalScopeProfile(UUID userId) {
        return accessResolutionService.getEffectiveProfileCodes(userId).stream()
            .anyMatch(GLOBAL_SCOPE_PROFILE_CODES::contains);
    }

    public boolean hasGlobalAnalyticsVisibility(UUID userId) {
        return hasPermissionName(userId, "ANALYTICS_READ")
            || hasPermissionName(userId, "REPORT_READ")
            || hasPermissionName(userId, "AUDIT_LOG_READ");
    }

    public boolean hasGlobalBusinessRead(UUID userId) {
        return hasGlobalScopeProfile(userId);
    }

    public boolean hasUnrestrictedProjectPortfolioManagement(UUID userId) {
        return hasGlobalScopeProfile(userId)
            && accessResolutionService.hasPermissionName(userId, "PROJECT_PORTFOLIO_MANAGE");
    }

    public boolean hasAdministrationOrHrVisibility(UUID userId) {
        return hasPermissionName(userId, "USER_READ")
            || hasPermissionName(userId, "EMPLOYEE_MANAGE")
            || hasPermissionName(userId, "ADMIN_REQUEST_INBOX_READ")
            || hasPermissionName(userId, "ADMIN_REQUEST_READ_GLOBAL");
    }

    public boolean hasProjectScopedManagement(UUID userId) {
        return hasPermissionName(userId, "PROJECT_ASSIGNMENT_MANAGE")
            || hasPermissionName(userId, "TEAM_MANAGE")
            || hasPermissionName(userId, "PROJECT_PORTFOLIO_MANAGE");
    }

    public boolean hasManagerInboxAccess(UUID userId) {
        return hasPermissionName(userId, "APPROVAL_STEP_READ")
            || hasPermissionName(userId, "LEAVE_REQUEST_READ")
            || hasPermissionName(userId, "ANALYTICS_READ");
    }

    private boolean hasManagerDepartmentVisibility(UUID userId) {
        return hasPermissionName(userId, "APPROVAL_STEP_READ")
            || hasPermissionName(userId, "LEAVE_REQUEST_READ")
            || hasPermissionName(userId, "EMPLOYEE_READ")
            || hasPermissionName(userId, "DEPARTMENT_READ")
            || hasPermissionName(userId, "TEAM_READ")
            || hasPermissionName(userId, "PROJECT_ASSIGNMENT_MANAGE")
            || hasPermissionName(userId, "LEAVE_BALANCE_READ_SCOPED")
            || hasPermissionName(userId, "ANALYTICS_READ");
    }

    private List<UUID> resolveDepartmentIdsFromScopeRefs(List<UUID> scopeRefIds) {
        if (scopeRefIds == null || scopeRefIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> departments = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();
        for (UUID scopeRefId : scopeRefIds) {
            if (scopeRefId == null) {
                continue;
            }

            departmentRepository.findById(scopeRefId)
                .filter(department -> department.isActive())
                .ifPresent(department -> departments.add(department.getId()));

            teamRepository.findById(scopeRefId)
                .filter(team -> team.isActive())
                .ifPresent(team -> departments.add(team.getDepartmentId()));

            teamHierarchyRelationRepository.findById(scopeRefId)
                .flatMap(relation -> teamRepository.findById(relation.getTeamId()))
                .filter(team -> team.isActive())
                .ifPresent(team -> departments.add(team.getDepartmentId()));

            projectAssignmentRepository.findById(scopeRefId)
                .filter(assignment -> assignment.isActive())
                .filter(assignment -> !assignment.getStartDate().isAfter(today))
                .filter(assignment -> assignment.getEndDate() == null || !assignment.getEndDate().isBefore(today))
                .ifPresent(assignment -> addProjectDepartments(departments, assignment.getProjectId()));

            addProjectDepartments(departments, scopeRefId);
        }
        return List.copyOf(departments);
    }

    private void addProjectDepartments(Set<UUID> departments, UUID projectId) {
        projectDepartmentRepository.findByProjectId(projectId).stream()
            .map(projectDepartment -> projectDepartment.getDepartmentId())
            .forEach(departments::add);
    }
}
