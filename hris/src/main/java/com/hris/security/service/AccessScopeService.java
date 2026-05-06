package com.hris.security.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessScopeService {

    private final UserRoleRepository userRoleRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<UserRole> getEffectiveRoles(UUID userId) {
        return userRoleRepository.findEffectiveByUserId(userId, Instant.now()).stream()
            .filter(userRole -> userRole.getRole() != null && userRole.getRole().isActive())
            .toList();
    }

    public boolean hasAnyRole(List<UserRole> roles, String... roleCodes) {
        return roles.stream()
            .map(UserRole::getRole)
            .filter(role -> role != null && role.getCode() != null)
            .map(role -> normalize(role.getCode()))
            .anyMatch(roleCode -> matchesAny(roleCode, roleCodes));
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

    public Optional<UUID> resolveDepartmentManagerDepartmentId(List<UserRole> roles, Employee employee) {
        if (!hasAnyRole(roles, "DEPT_MANAGER")) {
            return Optional.empty();
        }

        return roles.stream()
            .filter(userRole -> userRole.getRole() != null
                && "DEPT_MANAGER".equals(normalize(userRole.getRole().getCode())))
            .map(UserRole::getDepartmentId)
            .filter(departmentId -> departmentId != null)
            .findFirst()
            .or(() -> Optional.ofNullable(employee).map(Employee::getDepartmentId));
    }

    public boolean hasGlobalAnalyticsVisibility(List<UserRole> roles) {
        return hasAnyRole(roles, "ADMINISTRATION", "HR_ADMIN", "DIRECTOR");
    }

    public boolean hasAdministrationOrHrVisibility(List<UserRole> roles) {
        return hasAnyRole(roles, "ADMINISTRATION", "HR_ADMIN");
    }

    private boolean matchesAny(String roleCode, String... expectedRoleCodes) {
        for (String expectedRoleCode : expectedRoleCodes) {
            if (roleCode.equals(normalize(expectedRoleCode))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
