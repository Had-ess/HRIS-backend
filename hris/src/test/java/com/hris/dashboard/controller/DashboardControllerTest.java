package com.hris.dashboard.controller;

import com.hris.auth.entity.Permission;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.RolePermission;
import com.hris.auth.entity.UserRole;
import com.hris.auth.repository.PermissionRepository;
import com.hris.auth.repository.RolePermissionRepository;
import com.hris.auth.repository.UserRoleRepository;
import com.hris.common.ApiResponse;
import com.hris.dashboard.dto.DirectorDashboardDto;
import com.hris.dashboard.dto.EmployeeDashboardDto;
import com.hris.dashboard.dto.HrDashboardDto;
import com.hris.dashboard.dto.LeaveMetricsSummaryDto;
import com.hris.dashboard.service.DashboardService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.support.TestAuthenticationFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @Test
    @DisplayName("HR dashboard endpoint is denied without required permission")
    void hrDashboardEndpointDeniedWithoutRequiredPermission() {
        DashboardController controller = new DashboardController(
            dashboardService,
            new PermissionAuthorizationService(userRoleRepository, rolePermissionRepository, permissionRepository)
        );

        assertThatThrownBy(() -> controller.getHrDashboard(
            TestAuthenticationFactory.jwtAuthentication(UUID.randomUUID())))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Test
    @DisplayName("HR dashboard endpoint succeeds with required permission")
    void hrDashboardEndpointSucceedsWithRequiredPermission() {
        UUID userId = UUID.randomUUID();
        DashboardController controller = new DashboardController(
            dashboardService,
            new PermissionAuthorizationService(userRoleRepository, rolePermissionRepository, permissionRepository)
        );
        when(dashboardService.getHrDashboard()).thenReturn(
            new HrDashboardDto(3L, 4L, 5L, 2L, List.of())
        );
        stubPermission(userId, "DASHBOARD", "HR_VIEW");

        ResponseEntity<ApiResponse<HrDashboardDto>> response = controller.getHrDashboard(
            TestAuthenticationFactory.jwtAuthentication(userId)
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().pendingApprovalsCount()).isEqualTo(3L);
        assertThat(response.getBody().data().totalEmployees()).isEqualTo(5L);
        verify(dashboardService).getHrDashboard();
    }

    @Test
    @DisplayName("director dashboard endpoint protection behaves correctly")
    void directorDashboardEndpointProtectionBehavesCorrectly() {
        UUID userId = UUID.randomUUID();
        DashboardController controller = new DashboardController(
            dashboardService,
            new PermissionAuthorizationService(userRoleRepository, rolePermissionRepository, permissionRepository)
        );
        when(dashboardService.getDirectorDashboard()).thenReturn(
            new DirectorDashboardDto(9L, 3L, 2L, 1L,
                new LeaveMetricsSummaryDto("2026-04", 8, 6, 1, 2.5), 4L)
        );
        stubPermission(userId, "DASHBOARD", "DIRECTOR_VIEW");

        ResponseEntity<ApiResponse<DirectorDashboardDto>> response = controller.getDirectorDashboard(
            TestAuthenticationFactory.jwtAuthentication(userId)
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().activeProjectsCount()).isEqualTo(2L);
        assertThat(response.getBody().data().currentPeriodLeaveMetrics().period()).isEqualTo("2026-04");
    }

    @Test
    @DisplayName("administration fallback role can access director dashboard")
    void administrationFallbackRoleCanAccessDirectorDashboard() {
        UUID userId = UUID.randomUUID();
        DashboardController controller = new DashboardController(
            dashboardService,
            new PermissionAuthorizationService(userRoleRepository, rolePermissionRepository, permissionRepository)
        );
        when(dashboardService.getDirectorDashboard()).thenReturn(
            new DirectorDashboardDto(11L, 4L, 3L, 2L,
                new LeaveMetricsSummaryDto("2026-04", 10, 8, 1, 1.5), 5L)
        );

        ResponseEntity<ApiResponse<DirectorDashboardDto>> response = controller.getDirectorDashboard(
            TestAuthenticationFactory.jwtAuthentication(userId, "ADMINISTRATION")
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().totalEmployees()).isEqualTo(11L);
        verify(dashboardService).getDirectorDashboard();
    }

    @Test
    @DisplayName("employee dashboard endpoint returns wrapped payload")
    void employeeDashboardEndpointReturnsWrappedPayload() {
        UUID userId = UUID.randomUUID();
        DashboardController controller = new DashboardController(
            dashboardService,
            new PermissionAuthorizationService(userRoleRepository, rolePermissionRepository, permissionRepository)
        );
        when(dashboardService.getEmployeeDashboard(userId)).thenReturn(
            new EmployeeDashboardDto(2L, List.of(), List.of(), List.of())
        );

        ResponseEntity<ApiResponse<EmployeeDashboardDto>> response =
            controller.getMyDashboard(TestAuthenticationFactory.jwtAuthentication(userId, "EMPLOYEE"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().unreadNotificationsCount()).isEqualTo(2L);
    }

    private void stubPermission(UUID userId, String resource, String action) {
        UUID roleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        Role role = Role.builder().id(roleId).code("CUSTOM_DASHBOARD").name("Custom Dashboard").isActive(true).build();
        UserRole userRole = UserRole.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .roleId(roleId)
            .role(role)
            .isActive(true)
            .build();
        Permission permission = Permission.builder()
            .id(permissionId)
            .name(resource + "_" + action)
            .resource(resource)
            .action(action)
            .scope("GLOBAL")
            .isActive(true)
            .build();

        when(userRoleRepository.findEffectiveByUserId(eq(userId), any(Instant.class))).thenReturn(List.of(userRole));
        when(rolePermissionRepository.findByRoleIdIn(List.of(roleId))).thenReturn(List.of(
            RolePermission.builder().id(UUID.randomUUID()).roleId(roleId).permissionId(permissionId).build()
        ));
        when(permissionRepository.findByIdInAndIsActiveTrue(java.util.Set.of(permissionId))).thenReturn(List.of(permission));
    }
}
