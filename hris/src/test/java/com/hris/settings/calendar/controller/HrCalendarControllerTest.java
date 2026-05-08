package com.hris.settings.calendar.controller;

import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.security.PermissionAuthorizationService;
import com.hris.settings.calendar.dto.HrCalendarDto;
import com.hris.settings.calendar.service.HrCalendarService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HrCalendarController.class)
@Import({GlobalExceptionHandler.class, HrCalendarControllerTest.TestSecurityConfig.class})
class HrCalendarControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private HrCalendarService hrCalendarService;
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
    @DisplayName("GET /api/hr-calendars returns calendars")
    void getCalendarsReturnsCalendars() throws Exception {
        UUID calendarId = UUID.randomUUID();
        when(permissionAuthorizationService.hasPermission(any(), eq("HR_CALENDAR"), eq("READ"))).thenReturn(true);
        when(hrCalendarService.getAll()).thenReturn(List.of(
            new HrCalendarDto(calendarId, "TN", "Tunisia", "TN", "Africa/Tunis", 8, "MANUAL", true, null, null)
        ));

        mockMvc.perform(get("/api/hr-calendars").with(user(UUID.randomUUID().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(calendarId.toString()))
            .andExpect(jsonPath("$.data[0].code").value("TN"));
    }

    @Test
    @DisplayName("POST /api/hr-calendars enforces HR_CALENDAR_MANAGE")
    void createCalendarEnforcesPermission() throws Exception {
        doThrow(new AccessDeniedException("forbidden"))
            .when(permissionAuthorizationService).authorize(any(), eq("HR_CALENDAR"), eq("MANAGE"));

        mockMvc.perform(post("/api/hr-calendars")
                .with(user(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "TN",
                      "name": "Tunisia",
                      "country": "TN",
                      "timezone": "Africa/Tunis",
                      "hoursPerDay": 8,
                      "source": "MANUAL",
                      "active": true
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
