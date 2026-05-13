package com.hris.organisation.hierarchy.controller;

import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.organisation.hierarchy.dto.TeamHierarchyNodeDto;
import com.hris.organisation.hierarchy.service.TeamHierarchyService;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.security.PermissionAuthorizationService;
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

import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(controllers = TeamHierarchyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, TeamHierarchyControllerTest.TestSecurityConfig.class})
class TeamHierarchyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private TeamHierarchyService teamHierarchyService;
    @MockBean private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("GET /api/team-hierarchy returns computed hierarchy nodes")
    void getHierarchyReturnsComputedNodes() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        UUID collaboratorId = UUID.randomUUID();
        when(teamHierarchyService.getHierarchy(teamId)).thenReturn(List.of(
            new TeamHierarchyNodeDto(
                relationId,
                teamId,
                collaboratorId,
                "E001",
                "Alice Head",
                null,
                null,
                null,
                "RESPONSIBLE",
                1,
                2,
                "CHAIN_HEAD",
                "ACTIVE",
                LocalDate.of(2026, 1, 1),
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
            )
        ));

        mockMvc.perform(get("/api/team-hierarchy").param("teamId", teamId.toString()).with(user(UUID.randomUUID().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(relationId.toString()))
            .andExpect(jsonPath("$.data[0].role").value("RESPONSIBLE"))
            .andExpect(jsonPath("$.data[0].hierarchyStatus").value("CHAIN_HEAD"));
    }

    @Test
    @DisplayName("POST /api/team-hierarchy enforces TEAM_HIERARCHY_MANAGE")
    void createHierarchyRelationEnforcesPermission() throws Exception {
        doThrow(new AccessDeniedException("forbidden"))
            .when(permissionAuthorizationService).authorize(any(), eq("TEAM_HIERARCHY"), eq("MANAGE"));

        mockMvc.perform(post("/api/team-hierarchy")
                .with(user(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "teamId": "11111111-1111-1111-1111-111111111111",
                      "collaboratorEmployeeId": "22222222-2222-2222-2222-222222222222",
                      "responsibleEmployeeId": null,
                      "startDate": "2026-01-01",
                      "endDate": null
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
