package com.hris.analytics.service;

import com.hris.analytics.enums.ScopeType;
import com.hris.auth.entity.Employee;
import com.hris.common.ScopeFilter;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScopeFilterResolver {

    private final AccessScopeService accessScopeService;
    private final ProjectAssignmentRepository projectAssignmentRepository;

    @Transactional(readOnly = true)
    public ScopeFilter resolve(UUID userId) {
        List<com.hris.auth.entity.UserRole> roles = accessScopeService.getEffectiveRoles(userId);

        if (accessScopeService.hasGlobalAnalyticsVisibility(roles)) {
            return new ScopeFilter(ScopeType.ALL, null, List.of());
        }

        Employee employee = accessScopeService.getEmployeeOrThrow(userId);

        if (accessScopeService.hasAnyRole(roles, "DEPT_MANAGER")) {
            return new ScopeFilter(
                ScopeType.DEPARTMENT,
                accessScopeService.resolveDepartmentManagerDepartmentId(roles, employee)
                    .orElse(employee.getDepartmentId()),
                List.of()
            );
        }

        if (accessScopeService.hasAnyRole(roles, "PROJECT_SUPERVISOR")) {
            List<UUID> projectIds = projectAssignmentRepository.findActiveProjectIdsBySupervisorId(
                employee.getId(), LocalDate.now());
            return new ScopeFilter(ScopeType.PROJECT, null, projectIds);
        }

        return new ScopeFilter(ScopeType.OWN, employee.getId(), List.of(employee.getId()));
    }
}
