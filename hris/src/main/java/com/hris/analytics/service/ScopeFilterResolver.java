package com.hris.analytics.service;

import com.hris.analytics.enums.ScopeType;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.common.ScopeFilter;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScopeFilterResolver {

    private final UserRoleRepository userRoleRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;

    @Transactional(readOnly = true)
    public ScopeFilter resolve(UUID userId) {
        List<UserRole> roles = userRoleRepository.findEffectiveByUserId(userId, Instant.now());

        if (hasRole(roles, "ADMINISTRATION") || hasRole(roles, "HR_ADMIN") || hasRole(roles, "DIRECTOR")) {
            return new ScopeFilter(ScopeType.ALL, null, List.of());
        }

        Employee employee = employeeRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        if (hasRole(roles, "DEPT_MANAGER")) {
            return new ScopeFilter(ScopeType.DEPARTMENT, employee.getDepartmentId(), List.of());
        }

        if (hasRole(roles, "PROJECT_SUPERVISOR")) {
            List<UUID> projectIds = projectAssignmentRepository.findActiveProjectIdsBySupervisorId(
                employee.getId(), LocalDate.now());
            return new ScopeFilter(ScopeType.PROJECT, null, projectIds);
        }

        return new ScopeFilter(ScopeType.OWN, employee.getId(), List.of(employee.getId()));
    }

    private boolean hasRole(List<UserRole> roles, String roleCode) {
        return roles.stream().anyMatch(userRole ->
            userRole.getRole() != null && roleCode.equals(userRole.getRole().getCode()));
    }
}
