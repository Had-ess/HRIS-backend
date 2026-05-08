package com.hris.settings.quick.controller;

import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.security.PermissionAuthorizationService;
import com.hris.settings.quick.dto.QuickSettingsDto;
import com.hris.settings.quick.service.QuickSettingsService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QuickSettingsController.class)
@Import({GlobalExceptionHandler.class, QuickSettingsControllerTest.TestSecurityConfig.class})
class QuickSettingsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private QuickSettingsService quickSettingsService;
    @MockBean private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ((FilterChain) invocation.getArgument(2)).doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/settings/quick returns settings")
    void getReturnsSettings() throws Exception {
        UUID workflowId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        when(quickSettingsService.get()).thenReturn(new QuickSettingsDto(
            2, 4, 16, "MON_FRI", workflowId, "LEAVE_DEFAULT", "Leave default", 24, 48, calendarId, "TN", "Tunisia", 8, null
        ));

        mockMvc.perform(get("/api/settings/quick").with(user(UUID.randomUUID().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.defaultValidationWorkflowCode").value("LEAVE_DEFAULT"))
            .andExpect(jsonPath("$.data.activeCalendarCode").value("TN"));
    }

    @Test
    @DisplayName("PUT /api/settings/quick enforces SETTINGS_MANAGE")
    void updateEnforcesPermission() throws Exception {
        doThrow(new AccessDeniedException("forbidden"))
            .when(permissionAuthorizationService).authorize(any(), eq("SETTINGS"), eq("MANAGE"));

        mockMvc.perform(put("/api/settings/quick")
                .with(user(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "monthlyAcquisitionRate": 2,
                      "maxAuthorizationsPerMonth": 4,
                      "maxAuthorizationHours": 16,
                      "workWeekPattern": "MON_FRI",
                      "defaultWorkflowSlaHours": 24,
                      "defaultValidationSlaHours": 48,
                      "workingHoursPerDay": 8
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        org.springframework.security.web.SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
        }
    }
}
