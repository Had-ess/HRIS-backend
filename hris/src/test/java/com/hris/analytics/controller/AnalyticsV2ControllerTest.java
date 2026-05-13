package com.hris.analytics.controller;

import com.hris.analytics.dto.AnalyticsDashboardDto;
import com.hris.analytics.dto.AnalyticsScopeOptionDto;
import com.hris.analytics.dto.AnalyticsSummaryDto;
import com.hris.analytics.enums.AnalyticsScopeType;
import com.hris.analytics.service.AnalyticsQueryService;
import com.hris.analytics.service.AnalyticsScopeService;
import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.security.PermissionAuthorizationService;
import com.hris.support.TestAuthenticationFactory;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnalyticsV2Controller.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AnalyticsV2ControllerTest.TestSecurityConfig.class})
class AnalyticsV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AnalyticsScopeService analyticsScopeService;
    @MockBean private AnalyticsQueryService analyticsQueryService;
    @MockBean private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("summary endpoint enforces permission and scope-aware query")
    void summaryEndpointEnforcesPermissionAndScope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        doNothing().when(permissionAuthorizationService).authorizeAnyPermissionName(any(),
            eq("ANALYTICS_READ_OWN"),
            eq("ANALYTICS_READ_SCOPED"),
            eq("ANALYTICS_READ_GLOBAL"),
            eq("REPORT_READ"),
            eq("ANALYTICS_READ"));
        doNothing().when(analyticsScopeService).assertAccessible(userId, AnalyticsScopeType.EMPLOYEE, employeeId);
        when(analyticsQueryService.getSummary(any(), any(), eq(AnalyticsScopeType.EMPLOYEE), eq(employeeId)))
            .thenReturn(new AnalyticsSummaryDto(10, 2, 3, 1, 20, 0, 1));

        mockMvc.perform(get("/api/analytics/v2/summary")
                .with(TestAuthenticationFactory.jwtRequest(userId, "EMPLOYEE"))
                .param("scopeType", "EMPLOYEE")
                .param("scopeId", employeeId.toString())
                .param("from", "2026-05-01")
                .param("to", "2026-05-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.leaveRequests").value(10))
            .andExpect(jsonPath("$.data.pendingApprovals").value(3));

        verify(permissionAuthorizationService).authorizeAnyPermissionName(any(),
            eq("ANALYTICS_READ_OWN"),
            eq("ANALYTICS_READ_SCOPED"),
            eq("ANALYTICS_READ_GLOBAL"),
            eq("REPORT_READ"),
            eq("ANALYTICS_READ"));
        verify(analyticsScopeService).assertAccessible(userId, AnalyticsScopeType.EMPLOYEE, employeeId);
    }

    @Test
    @DisplayName("scopes endpoint returns resolved scope list")
    void scopesEndpointReturnsResolvedScopeList() throws Exception {
        UUID userId = UUID.randomUUID();

        doNothing().when(permissionAuthorizationService).authorizeAnyPermissionName(any(),
            eq("ANALYTICS_READ_OWN"),
            eq("ANALYTICS_READ_SCOPED"),
            eq("ANALYTICS_READ_GLOBAL"),
            eq("REPORT_READ"),
            eq("ANALYTICS_READ"));
        when(analyticsScopeService.getAvailableScopes(userId)).thenReturn(List.of(
            new AnalyticsScopeOptionDto(AnalyticsScopeType.EMPLOYEE, UUID.randomUUID(), "My analytics"),
            new AnalyticsScopeOptionDto(AnalyticsScopeType.GLOBAL, null, "Global")
        ));

        mockMvc.perform(get("/api/analytics/v2/scopes")
                .with(TestAuthenticationFactory.jwtRequest(userId, "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].scopeType").value("EMPLOYEE"))
            .andExpect(jsonPath("$.data[1].scopeType").value("GLOBAL"));
    }

    @Test
    @DisplayName("dashboard endpoint supports default scope resolution")
    void dashboardEndpointSupportsDefaultScope() throws Exception {
        UUID userId = UUID.randomUUID();

        doNothing().when(permissionAuthorizationService).authorizeAnyPermissionName(any(),
            eq("ANALYTICS_READ_OWN"),
            eq("ANALYTICS_READ_SCOPED"),
            eq("ANALYTICS_READ_GLOBAL"),
            eq("REPORT_READ"),
            eq("ANALYTICS_READ"));
        when(analyticsQueryService.getDashboard(eq(userId), eq(null), eq(null), any(), any()))
            .thenReturn(new AnalyticsDashboardDto(AnalyticsScopeType.EMPLOYEE, userId, "My analytics", 1, 2, 0, 0, 12));

        mockMvc.perform(get("/api/analytics/v2/dashboard")
                .with(TestAuthenticationFactory.jwtRequest(userId, "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scopeType").value("EMPLOYEE"))
            .andExpect(jsonPath("$.data.availableBalanceDays").value(12));
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        org.springframework.security.web.SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .build();
        }
    }
}
