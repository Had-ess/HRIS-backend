package com.hris.leave.controller;

import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.leave.dto.LeaveTypeDto;
import com.hris.leave.service.LeaveAcquisitionPolicyService;
import com.hris.leave.service.LeaveTypeService;
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LeaveTypeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, LeaveTypeControllerTest.TestSecurityConfig.class})
class LeaveTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveTypeService leaveTypeService;
    @MockBean
    private LeaveAcquisitionPolicyService leaveAcquisitionPolicyService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private UserProvisioningService userProvisioningService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockBean
    private PermissionAuthorizationService permissionAuthorizationService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("list returns leave type DTOs")
    void listReturnsLeaveTypeDtos() throws Exception {
        UUID typeId = UUID.randomUUID();
        when(leaveTypeService.getAllActive()).thenReturn(List.of(
            new LeaveTypeDto(typeId, "ANNUAL", "Annual Leave", true, false, true, true, null, null, null)
        ));

        mockMvc.perform(get("/api/leave-types").with(user(UUID.randomUUID().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(typeId.toString()))
            .andExpect(jsonPath("$.data[0].code").value("ANNUAL"))
            .andExpect(jsonPath("$.data[0].isPaid").value(true))
            .andExpect(jsonPath("$.data[0].requiresJustification").value(false))
            .andExpect(jsonPath("$.data[0].isActive").value(true));

        verify(permissionAuthorizationService).authorize(any(), eq("LEAVE_TYPE"), eq("READ"));
    }

    @Test
    @DisplayName("create delegates to service and returns DTO response shape")
    void createDelegatesToServiceAndReturnsDtoResponseShape() throws Exception {
        UUID typeId = UUID.randomUUID();
        when(leaveTypeService.create(any())).thenReturn(
            new LeaveTypeDto(typeId, "SICK", "Sick Leave", true, true, true, true, null, null, null)
        );

        mockMvc.perform(post("/api/leave-types")
                .with(user(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "SICK",
                      "name": "Sick Leave",
                      "isPaid": true,
                      "requiresJustification": true,
                      "isActive": true,
                      "balanceTracked": true
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(typeId.toString()))
            .andExpect(jsonPath("$.data.code").value("SICK"))
            .andExpect(jsonPath("$.data.name").value("Sick Leave"));

        verify(leaveTypeService).create(any());
    }

    @Test
    @DisplayName("create rejects invalid payload")
    void createRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/leave-types")
                .with(user(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "",
                      "name": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @DisplayName("update delegates to service and validates payload")
    void updateDelegatesToServiceAndValidatesPayload() throws Exception {
        UUID typeId = UUID.randomUUID();
        when(leaveTypeService.update(eq(typeId), any())).thenReturn(
            new LeaveTypeDto(typeId, "UNPAID", "Unpaid Leave", false, false, true, false, null, null, null)
        );

        mockMvc.perform(patch("/api/leave-types/{id}", typeId)
                .with(user(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "UNPAID",
                      "name": "Unpaid Leave",
                      "isPaid": false,
                      "requiresJustification": false,
                      "isActive": true,
                      "balanceTracked": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("UNPAID"))
            .andExpect(jsonPath("$.data.isPaid").value(false));

        verify(leaveTypeService).update(eq(typeId), any());
    }

    @Test
    @DisplayName("update rejects invalid payload")
    void updateRejectsInvalidPayload() throws Exception {
        UUID typeId = UUID.randomUUID();

        mockMvc.perform(patch("/api/leave-types/{id}", typeId)
                .with(user(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "",
                      "name": "",
                      "isPaid": null,
                      "requiresJustification": null,
                      "isActive": null,
                      "balanceTracked": null
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        org.springframework.security.web.SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
        }
    }
}
