package com.hris.settings.validation.controller;

import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.common.PageResponse;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.security.PermissionAuthorizationService;
import com.hris.settings.validation.dto.ValidationWorkflowDto;
import com.hris.settings.validation.dto.ValidationWorkflowOptionsDto;
import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.service.ValidationWorkflowService;
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

@WebMvcTest(controllers = ValidationWorkflowController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, ValidationWorkflowControllerTest.TestSecurityConfig.class})
class ValidationWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ValidationWorkflowService validationWorkflowService;
    @MockBean private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("GET /api/validation-workflows returns workflows")
    void getWorkflowsReturnsPage() throws Exception {
        UUID workflowId = UUID.randomUUID();
        when(validationWorkflowService.getAll(any())).thenReturn(new PageResponse<>(
            List.of(new ValidationWorkflowDto(
                workflowId,
                "LEAVE_STANDARD",
                "Leave standard",
                "LEAVE",
                "TEAM_HIERARCHY",
                "ONE_REQUIRED",
                null,
                "HR_QUEUE",
                null,
                null,
                true,
                false,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
            )),
            0, 20, 1, 1, true, true
        ));
        when(permissionAuthorizationService.hasPermission(any(), eq("VALIDATION_WORKFLOW"), eq("READ"))).thenReturn(true);

        mockMvc.perform(get("/api/validation-workflows").with(user(UUID.randomUUID().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].code").value("LEAVE_STANDARD"))
            .andExpect(jsonPath("$.data.content[0].validatorSource").value("TEAM_HIERARCHY"));
    }

    @Test
    @DisplayName("GET /api/validation-workflows/options returns LEAVE options")
    void getOptionsReturnsLeaveOptions() throws Exception {
        when(validationWorkflowService.getOptions(ValidationUsage.LEAVE)).thenReturn(
            new ValidationWorkflowOptionsDto(
                List.of("LEAVE"),
                List.of("TEAM_HIERARCHY"),
                List.of("ONE_REQUIRED", "ALL_REQUIRED"),
                List.of("HR_QUEUE", "BLOCK_SUBMISSION"),
                List.of(),
                List.of()
            )
        );
        when(permissionAuthorizationService.hasPermission(any(), eq("VALIDATION_WORKFLOW"), eq("READ"))).thenReturn(true);

        mockMvc.perform(get("/api/validation-workflows/options")
                .param("usage", "LEAVE")
                .with(user(UUID.randomUUID().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.usages[0]").value("LEAVE"))
            .andExpect(jsonPath("$.data.validatorSources[0]").value("TEAM_HIERARCHY"));
    }

    @Test
    @DisplayName("POST /api/validation-workflows enforces VALIDATION_WORKFLOW_MANAGE")
    void createWorkflowEnforcesPermission() throws Exception {
        doThrow(new AccessDeniedException("forbidden"))
            .when(permissionAuthorizationService).authorize(any(), eq("VALIDATION_WORKFLOW"), eq("MANAGE"));

        mockMvc.perform(post("/api/validation-workflows")
                .with(user(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "LEAVE_STANDARD",
                      "name": "Leave standard",
                      "usage": "LEAVE",
                      "validatorSource": "TEAM_HIERARCHY",
                      "validationMode": "ONE_REQUIRED",
                      "minValidators": null,
                      "fallbackMode": "HR_QUEUE",
                      "fallbackProfileId": null,
                      "fallbackPermissionCode": null,
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
