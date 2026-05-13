package com.hris.organisation.controller;

import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.common.PageResponse;
import com.hris.organisation.dto.TeamDto;
import com.hris.organisation.service.TeamService;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.security.PermissionAuthorizationService;
import com.hris.support.TestAuthenticationFactory;
import jakarta.servlet.FilterChain;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TeamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, TeamControllerTest.TestSecurityConfig.class})
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private TeamService teamService;
    @MockBean private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("GET /api/teams returns paged teams")
    void getTeamsReturnsPagedTeams() throws Exception {
        UUID teamId = UUID.randomUUID();
        when(teamService.getAll(any(), any())).thenReturn(new PageResponse<>(
            List.of(new TeamDto(teamId, "ENG", "Engineering", UUID.randomUUID(), "Technology", "TECH", UUID.randomUUID(), "E001", "Alice Head", true)),
            0, 20, 1, 1, true, true
        ));

        mockMvc.perform(get("/api/teams").with(TestAuthenticationFactory.jwtRequest(UUID.randomUUID(), "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(teamId.toString()))
            .andExpect(jsonPath("$.data.content[0].code").value("ENG"));
    }

    @Test
    @DisplayName("POST /api/teams enforces TEAM_MANAGE")
    void createTeamEnforcesPermission() throws Exception {
        doThrow(new AccessDeniedException("forbidden"))
            .when(permissionAuthorizationService).authorize(any(), eq("TEAM"), eq("MANAGE"));

        mockMvc.perform(post("/api/teams")
                .with(TestAuthenticationFactory.jwtRequest(UUID.randomUUID(), "EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "ENG",
                      "name": "Engineering",
                      "departmentId": "11111111-1111-1111-1111-111111111111",
                      "supervisorEmployeeId": "22222222-2222-2222-2222-222222222222"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/teams/{id} delegates to service")
    void deleteTeamDelegatesToService() throws Exception {
        UUID teamId = UUID.randomUUID();

        mockMvc.perform(delete("/api/teams/{id}", teamId).with(TestAuthenticationFactory.jwtRequest(UUID.randomUUID(), "EMPLOYEE")))
            .andExpect(status().isNoContent());

        verify(teamService).deactivate(eq(teamId), any());
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
