package com.hris.security.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccessScopeService Unit Tests")
class AccessScopeServiceTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private AccessScopeService accessScopeService;

    private UUID userId;
    private UUID employeeId;
    private UUID employeeDepartmentId;
    private UUID managedDepartmentId;
    private Employee employee;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        employeeDepartmentId = UUID.randomUUID();
        managedDepartmentId = UUID.randomUUID();
        employee = Employee.builder()
            .id(employeeId)
            .userId(userId)
            .departmentId(employeeDepartmentId)
            .build();
    }

    @Test
    @DisplayName("getEffectiveRoles keeps only roles with active role entities")
    void getEffectiveRolesKeepsOnlyActiveRoleEntities() {
        when(userRoleRepository.findEffectiveByUserId(eq(userId), any(Instant.class))).thenReturn(List.of(
            UserRole.builder()
                .role(Role.builder().code("HR_ADMIN").isActive(true).build())
                .build(),
            UserRole.builder()
                .role(Role.builder().code("DIRECTOR").isActive(false).build())
                .build(),
            UserRole.builder().build()
        ));

        List<UserRole> result = accessScopeService.getEffectiveRoles(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getRole().getCode()).isEqualTo("HR_ADMIN");
    }

    @Test
    @DisplayName("hasAnyRole matches role codes case insensitively")
    void hasAnyRoleMatchesCaseInsensitively() {
        List<UserRole> roles = List.of(
            UserRole.builder()
                .role(Role.builder().code("project_supervisor").isActive(true).build())
                .build()
        );

        assertThat(accessScopeService.hasAnyRole(roles, "PROJECT_SUPERVISOR")).isTrue();
    }

    @Test
    @DisplayName("global analytics visibility includes director")
    void globalAnalyticsVisibilityIncludesDirector() {
        List<UserRole> roles = List.of(
            UserRole.builder()
                .role(Role.builder().code("DIRECTOR").isActive(true).build())
                .build()
        );

        assertThat(accessScopeService.hasGlobalAnalyticsVisibility(roles)).isTrue();
    }

    @Test
    @DisplayName("administration or HR visibility excludes director")
    void administrationOrHrVisibilityExcludesDirector() {
        List<UserRole> roles = List.of(
            UserRole.builder()
                .role(Role.builder().code("DIRECTOR").isActive(true).build())
                .build()
        );

        assertThat(accessScopeService.hasAdministrationOrHrVisibility(roles)).isFalse();
    }

    @Test
    @DisplayName("department manager scope prefers role department")
    void departmentManagerScopePrefersRoleDepartment() {
        List<UserRole> roles = List.of(
            UserRole.builder()
                .role(Role.builder().code("DEPT_MANAGER").isActive(true).build())
                .departmentId(managedDepartmentId)
                .build()
        );

        assertThat(accessScopeService.resolveDepartmentManagerDepartmentId(roles, employee))
            .contains(managedDepartmentId);
    }

    @Test
    @DisplayName("department manager scope falls back to employee department")
    void departmentManagerScopeFallsBackToEmployeeDepartment() {
        List<UserRole> roles = List.of(
            UserRole.builder()
                .role(Role.builder().code("DEPT_MANAGER").isActive(true).build())
                .departmentId(null)
                .build()
        );

        assertThat(accessScopeService.resolveDepartmentManagerDepartmentId(roles, employee))
            .contains(employeeDepartmentId);
    }

    @Test
    @DisplayName("department manager scope stays empty without manager role")
    void departmentManagerScopeStaysEmptyWithoutManagerRole() {
        List<UserRole> roles = List.of(
            UserRole.builder()
                .role(Role.builder().code("PROJECT_SUPERVISOR").isActive(true).build())
                .build()
        );

        assertThat(accessScopeService.resolveDepartmentManagerDepartmentId(roles, employee)).isEmpty();
    }

    @Test
    @DisplayName("getEmployeeOrThrow returns linked employee")
    void getEmployeeOrThrowReturnsLinkedEmployee() {
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));

        Employee result = accessScopeService.getEmployeeOrThrow(userId);

        assertThat(result.getId()).isEqualTo(employeeId);
    }

    @Test
    @DisplayName("getEmployeeOrThrow preserves missing employee semantics")
    void getEmployeeOrThrowPreservesMissingEmployeeSemantics() {
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessScopeService.getEmployeeOrThrow(userId))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("Employee not found");
    }
}
