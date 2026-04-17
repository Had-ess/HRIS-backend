package com.hris.dashboard.controller;

import com.hris.common.ApiResponse;
import com.hris.dashboard.dto.DirectorDashboardDto;
import com.hris.dashboard.dto.EmployeeDashboardDto;
import com.hris.dashboard.dto.HrDashboardDto;
import com.hris.dashboard.dto.LeaveMetricsSummaryDto;
import com.hris.dashboard.service.DashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController dashboardController;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("employee dashboard endpoint returns wrapped payload")
    void employeeDashboardEndpointReturnsWrappedPayload() {
        UUID userId = UUID.randomUUID();
        when(dashboardService.getEmployeeDashboard(userId)).thenReturn(
            new EmployeeDashboardDto(2L, List.of(), List.of(), List.of())
        );

        ResponseEntity<ApiResponse<EmployeeDashboardDto>> response =
            dashboardController.getMyDashboard(TestAuthenticationFactory.jwtAuthentication(userId, "EMPLOYEE"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().unreadNotificationsCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("unauthorized access to HR dashboard is rejected")
    void unauthorizedAccessToHrDashboardIsRejected() {
        DashboardController securedController = securedController();
        SecurityContextHolder.getContext().setAuthentication(
            TestAuthenticationFactory.jwtAuthentication(UUID.randomUUID(), "EMPLOYEE"));

        assertThatThrownBy(securedController::getHrDashboard)
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("unauthorized access to director dashboard is rejected")
    void unauthorizedAccessToDirectorDashboardIsRejected() {
        DashboardController securedController = securedController();
        SecurityContextHolder.getContext().setAuthentication(
            TestAuthenticationFactory.jwtAuthentication(UUID.randomUUID(), "EMPLOYEE"));

        assertThatThrownBy(securedController::getDirectorDashboard)
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("HR admin can access HR dashboard")
    void hrAdminCanAccessHrDashboard() {
        DashboardController securedController = securedController();
        SecurityContextHolder.getContext().setAuthentication(
            TestAuthenticationFactory.jwtAuthentication(UUID.randomUUID(), "HR_ADMIN"));
        when(dashboardService.getHrDashboard()).thenReturn(
            new HrDashboardDto(3L, 4L, 5L, 2L, List.of())
        );

        ResponseEntity<ApiResponse<HrDashboardDto>> response = securedController.getHrDashboard();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().pendingApprovalsCount()).isEqualTo(3L);
        assertThat(response.getBody().data().totalEmployees()).isEqualTo(5L);
    }

    @Test
    @DisplayName("director can access director dashboard")
    void directorCanAccessDirectorDashboard() {
        DashboardController securedController = securedController();
        SecurityContextHolder.getContext().setAuthentication(
            TestAuthenticationFactory.jwtAuthentication(UUID.randomUUID(), "DIRECTOR"));
        when(dashboardService.getDirectorDashboard()).thenReturn(
            new DirectorDashboardDto(9L, 3L, 2L, 1L,
                new LeaveMetricsSummaryDto("2026-04", 8, 6, 1, 2.5), 4L)
        );

        ResponseEntity<ApiResponse<DirectorDashboardDto>> response = securedController.getDirectorDashboard();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().activeProjectsCount()).isEqualTo(2L);
        assertThat(response.getBody().data().currentPeriodLeaveMetrics().period()).isEqualTo("2026-04");
    }

    private DashboardController securedController() {
        ProxyFactory proxyFactory = new ProxyFactory(dashboardController);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (DashboardController) proxyFactory.getProxy();
    }
}
