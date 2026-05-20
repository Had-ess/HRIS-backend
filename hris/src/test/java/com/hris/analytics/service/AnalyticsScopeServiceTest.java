package com.hris.analytics.service;

import com.hris.access.service.AccessResolutionService;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.analytics.dto.AnalyticsScopeOptionDto;
import com.hris.analytics.enums.AnalyticsScopeType;
import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.organisation.entity.Team;
import com.hris.organisation.repository.TeamRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsScopeServiceTest {

    @Mock private AccessResolutionService accessResolutionService;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private AdminRequestTypeRepository adminRequestTypeRepository;

    @InjectMocks
    private AnalyticsScopeService analyticsScopeService;

    @Test
    @DisplayName("self-service analytics resolves own employee scope only")
    void selfServiceResolvesOwnEmployeeScopeOnly() {
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Employee employee = Employee.builder().id(employeeId).userId(userId).build();

        when(accessResolutionService.getEffectivePermissionNames(userId)).thenReturn(Set.of("ANALYTICS_READ_OWN"));
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));

        List<AnalyticsScopeOptionDto> scopes = analyticsScopeService.getAvailableScopes(userId);

        assertThat(scopes).containsExactly(
            new AnalyticsScopeOptionDto(AnalyticsScopeType.EMPLOYEE, employeeId, "My analytics")
        );
    }

    @Test
    @DisplayName("scoped analytics includes managed departments and teams")
    void scopedAnalyticsIncludesManagedDepartmentsAndTeams() {
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Employee employee = Employee.builder().id(employeeId).userId(userId).build();

        when(accessResolutionService.getEffectivePermissionNames(userId)).thenReturn(Set.of("ANALYTICS_READ_OWN", "ANALYTICS_READ_SCOPED"));
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
        when(departmentRepository.findByHeadEmployeeIdAndIsActiveTrue(employeeId)).thenReturn(List.of(
            Department.builder().id(departmentId).name("Operations").isActive(true).build()
        ));
        when(teamRepository.findBySupervisorEmployeeIdAndIsActiveTrue(employeeId)).thenReturn(List.of(
            Team.builder().id(teamId).projectId(UUID.randomUUID()).name("North Team").isActive(true).build()
        ));

        List<AnalyticsScopeOptionDto> scopes = analyticsScopeService.getAvailableScopes(userId);

        assertThat(scopes).contains(
            new AnalyticsScopeOptionDto(AnalyticsScopeType.EMPLOYEE, employeeId, "My analytics"),
            new AnalyticsScopeOptionDto(AnalyticsScopeType.DEPARTMENT, departmentId, "Operations"),
            new AnalyticsScopeOptionDto(AnalyticsScopeType.TEAM, teamId, "North Team")
        );
    }

    @Test
    @DisplayName("global analytics exposes global scope and filter catalogs")
    void globalAnalyticsExposesGlobalScopeAndFilters() {
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID leaveTypeId = UUID.randomUUID();
        UUID adminTypeId = UUID.randomUUID();
        Employee employee = Employee.builder().id(employeeId).userId(userId).build();

        when(accessResolutionService.getEffectivePermissionNames(userId)).thenReturn(Set.of("ANALYTICS_READ_GLOBAL"));
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
        when(departmentRepository.findByHeadEmployeeIdAndIsActiveTrue(employeeId)).thenReturn(List.of());
        when(teamRepository.findBySupervisorEmployeeIdAndIsActiveTrue(employeeId)).thenReturn(List.of());
        when(departmentRepository.findAll()).thenReturn(List.of(
            Department.builder().id(UUID.randomUUID()).code("OPS").name("Operations").isActive(true).build()
        ));
        when(teamRepository.findAll()).thenReturn(List.of(
            Team.builder().id(UUID.randomUUID()).code("NORTH").projectId(UUID.randomUUID()).name("North Team").isActive(true).build()
        ));
        when(leaveTypeRepository.findAll()).thenReturn(List.of(
            LeaveType.builder().id(leaveTypeId).code("ANNUAL").name("Annual Leave").isActive(true).balanceTracked(true).build()
        ));
        when(adminRequestTypeRepository.findAll()).thenReturn(List.of(
            AdminRequestType.builder().id(adminTypeId).code("CERT").name("Certificate").isActive(true).build()
        ));

        var filters = analyticsScopeService.getFilters(userId);

        assertThat(filters.scopes()).contains(new AnalyticsScopeOptionDto(AnalyticsScopeType.GLOBAL, null, "Global"));
        assertThat(filters.leaveTypes()).hasSize(1);
        assertThat(filters.adminRequestTypes()).hasSize(1);
        assertThat(filters.departments()).hasSize(1);
        assertThat(filters.teams()).hasSize(1);
    }

    @Test
    @DisplayName("assert accessible rejects unauthorized scope")
    void assertAccessibleRejectsUnauthorizedScope() {
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Employee employee = Employee.builder().id(employeeId).userId(userId).build();

        when(accessResolutionService.getEffectivePermissionNames(userId)).thenReturn(Set.of("ANALYTICS_READ_OWN"));
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> analyticsScopeService.assertAccessible(userId, AnalyticsScopeType.GLOBAL, null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("Analytics scope is not accessible");
    }

    @Test
    @DisplayName("missing employee fails scope resolution")
    void missingEmployeeFailsScopeResolution() {
        UUID userId = UUID.randomUUID();

        when(accessResolutionService.getEffectivePermissionNames(userId)).thenReturn(Set.of("ANALYTICS_READ_OWN"));
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analyticsScopeService.getAvailableScopes(userId))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("Employee not found");
    }
}
