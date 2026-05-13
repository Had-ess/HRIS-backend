package com.hris.security.service;

import com.hris.access.service.AccessResolutionService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessScopeService {

    private final AccessResolutionService accessResolutionService;
    private final EmployeeRepository employeeRepository;

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
        if (!hasManagerDepartmentVisibility(userId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(employee).map(Employee::getDepartmentId);
    }

    public boolean hasGlobalAnalyticsVisibility(UUID userId) {
        return hasPermissionName(userId, "ANALYTICS_READ_GLOBAL")
            || hasPermissionName(userId, "REPORT_READ")
            || hasPermissionName(userId, "AUDIT_LOG_READ");
    }

    public boolean hasGlobalBusinessRead(UUID userId) {
        return hasPermissionName(userId, "EMPLOYEE_MANAGE")
            || hasPermissionName(userId, "PROJECT_PORTFOLIO_MANAGE")
            || hasPermissionName(userId, "DEPARTMENT_MANAGE")
            || hasPermissionName(userId, "LEAVE_BALANCE_MANAGE")
            || hasPermissionName(userId, "ADMIN_REQUEST_READ_GLOBAL")
            || hasPermissionName(userId, "ANALYTICS_READ_GLOBAL");
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
            || hasPermissionName(userId, "ANALYTICS_READ_SCOPED");
    }

    private boolean hasManagerDepartmentVisibility(UUID userId) {
        return hasPermissionName(userId, "APPROVAL_STEP_READ")
            || hasPermissionName(userId, "LEAVE_REQUEST_READ")
            || hasPermissionName(userId, "LEAVE_BALANCE_READ_SCOPED")
            || hasPermissionName(userId, "ANALYTICS_READ_SCOPED");
    }
}
