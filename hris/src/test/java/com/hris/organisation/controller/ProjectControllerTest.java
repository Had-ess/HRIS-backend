package com.hris.organisation.controller;

import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.organisation.dto.ProjectResponseDto;
import com.hris.organisation.enums.ProjectStatus;
import com.hris.organisation.service.ProjectService;
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, ProjectControllerTest.TestSecurityConfig.class})
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ProjectService projectService;
    @MockBean private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("GET /api/projects/{id} returns the project payload")
    void getByIdReturnsProjectPayload() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(projectService.getById(any(), any())).thenReturn(new ProjectResponseDto(
            projectId,
            "HRIS Core",
            "HRIS-CORE",
            ProjectStatus.ACTIVE,
            LocalDate.of(2026, 1, 1),
            null
        ));

        mockMvc.perform(get("/api/projects/{id}", projectId).with(TestAuthenticationFactory.jwtRequest(UUID.randomUUID(), "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(projectId.toString()))
            .andExpect(jsonPath("$.data.name").value("HRIS Core"))
            .andExpect(jsonPath("$.data.code").value("HRIS-CORE"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/projects accepts the aligned payload shape")
    void createAcceptsAlignedPayloadShape() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(projectService.create(any(), any())).thenReturn(new ProjectResponseDto(
            projectId,
            "HRIS Core",
            "HRIS-CORE",
            ProjectStatus.ACTIVE,
            LocalDate.of(2026, 1, 1),
            null
        ));

        mockMvc.perform(post("/api/projects")
                .with(TestAuthenticationFactory.jwtRequest(UUID.randomUUID(), "EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "HRIS Core",
                      "code": "HRIS-CORE",
                      "status": "ACTIVE",
                      "startDate": "2026-01-01",
                      "endDate": null
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(projectId.toString()))
            .andExpect(jsonPath("$.data.code").value("HRIS-CORE"));
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
