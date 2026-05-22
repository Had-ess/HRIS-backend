package com.hris.analytics.controller;

import com.hris.analytics.dto.AnalyticsDashboardDto;
import com.hris.analytics.dto.AnalyticsOverviewDto;
import com.hris.analytics.dto.AnalyticsOverviewKpiDto;
import com.hris.analytics.dto.AnalyticsSummaryDto;
import com.hris.analytics.service.AnalyticsQueryService;
import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.security.PermissionAuthorizationService;
import com.hris.support.TestAuthenticationFactory;
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

    @MockBean private AnalyticsQueryService analyticsQueryService;
    @MockBean private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("summary endpoint enforces ANALYTICS_READ permission and returns org-wide counts")
    void summaryEndpointEnforcesPermissionAndReturnsCounts() throws Exception {
        UUID userId = UUID.randomUUID();

        doNothing().when(permissionAuthorizationService).authorizeAnyPermissionName(any(),
            eq("ANALYTICS_READ"),
            eq("REPORT_READ"));
        when(analyticsQueryService.getSummary(any(), any()))
            .thenReturn(new AnalyticsSummaryDto(10, 2, 3, 1, 20, 0, 1));

        mockMvc.perform(get("/api/analytics/v2/summary")
                .with(TestAuthenticationFactory.jwtRequest(userId, "EMPLOYEE"))
                .param("from", "2026-05-01")
                .param("to", "2026-05-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.leaveRequests").value(10))
            .andExpect(jsonPath("$.data.pendingApprovals").value(3));

        verify(permissionAuthorizationService).authorizeAnyPermissionName(any(),
            eq("ANALYTICS_READ"),
            eq("REPORT_READ"));
    }

    @Test
    @DisplayName("dashboard endpoint returns org-wide aggregates")
    void dashboardEndpointReturnsOrgWideAggregates() throws Exception {
        UUID userId = UUID.randomUUID();

        doNothing().when(permissionAuthorizationService).authorizeAnyPermissionName(any(),
            eq("ANALYTICS_READ"),
            eq("REPORT_READ"));
        when(analyticsQueryService.getDashboard(any(), any()))
            .thenReturn(new AnalyticsDashboardDto(1, 2, 0, 0, 12));

        mockMvc.perform(get("/api/analytics/v2/dashboard")
                .with(TestAuthenticationFactory.jwtRequest(userId, "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.availableBalanceDays").value(12));
    }

    @Test
    @DisplayName("overview endpoint enforces ANALYTICS_READ and returns consolidated module data")
    void overviewEndpointEnforcesPermissionAndReturnsModules() throws Exception {
        UUID userId = UUID.randomUUID();

        doNothing().when(permissionAuthorizationService).authorizeAnyPermissionName(any(),
            eq("ANALYTICS_READ"),
            eq("REPORT_READ"));
        when(analyticsQueryService.getOverview(any(), any()))
            .thenReturn(new AnalyticsOverviewDto(
                List.of(new AnalyticsOverviewKpiDto("headcount", "Headcount", "105", "+22% YoY", "people", "positive")),
                List.of(),
                List.of(),
                List.of(),
                List.of()
            ));

        mockMvc.perform(get("/api/analytics/v2/overview")
                .with(TestAuthenticationFactory.jwtRequest(userId, "HR_ADMIN"))
                .param("from", "2026-05-01")
                .param("to", "2026-05-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kpis[0].key").value("headcount"));
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
