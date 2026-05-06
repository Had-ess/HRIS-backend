package com.hris.analytics.service;

import com.hris.analytics.enums.ScopeType;
import com.hris.auth.entity.Employee;
import com.hris.common.ScopeFilter;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScopeFilterResolver Unit Tests")
class ScopeFilterResolverTest {

    @Mock
    private AccessScopeService accessScopeService;

    @Mock
    private ProjectAssignmentRepository projectAssignmentRepository;

    @InjectMocks
    private ScopeFilterResolver scopeFilterResolver;

    private UUID userId;
    private UUID employeeDepartmentId;
    private UUID managedDepartmentId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        employeeDepartmentId = UUID.randomUUID();
        managedDepartmentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("department manager scope uses role department when available")
    void departmentManagerScopeUsesRoleDepartmentWhenAvailable() {
        Employee employee = Employee.builder()
            .userId(userId)
            .departmentId(employeeDepartmentId)
            .build();
        when(accessScopeService.getEffectiveRoles(userId)).thenReturn(List.of());
        when(accessScopeService.hasGlobalAnalyticsVisibility(List.of())).thenReturn(false);
        when(accessScopeService.getEmployeeOrThrow(userId)).thenReturn(employee);
        when(accessScopeService.hasAnyRole(List.of(), "DEPT_MANAGER")).thenReturn(true);
        when(accessScopeService.resolveDepartmentManagerDepartmentId(List.of(), employee))
            .thenReturn(java.util.Optional.of(managedDepartmentId));

        ScopeFilter result = scopeFilterResolver.resolve(userId);

        assertThat(result.type()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(result.entityId()).isEqualTo(managedDepartmentId);
        assertThat(result.entityId()).isNotEqualTo(employeeDepartmentId);
    }
}
