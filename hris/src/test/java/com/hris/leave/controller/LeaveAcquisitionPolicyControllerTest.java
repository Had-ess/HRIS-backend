package com.hris.leave.controller;

import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.leave.acquisition.dto.LeaveAcquisitionPolicyDto;
import com.hris.leave.acquisition.entity.AcquisitionFrequency;
import com.hris.leave.service.LeaveAcquisitionPolicyService;
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

@WebMvcTest(controllers = LeaveAcquisitionPolicyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, LeaveAcquisitionPolicyControllerTest.TestSecurityConfig.class})
class LeaveAcquisitionPolicyControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private LeaveAcquisitionPolicyService service;
    @MockBean private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("GET /api/leave-acquisition-policies returns policies")
    void getPoliciesReturnsPolicies() throws Exception {
        UUID policyId = UUID.randomUUID();
        UUID leaveTypeId = UUID.randomUUID();
        when(permissionAuthorizationService.hasPermission(any(), eq("ACQUISITION_POLICY"), eq("READ"))).thenReturn(true);
        when(service.getAll()).thenReturn(List.of(new LeaveAcquisitionPolicyDto(
            policyId,
            "ANNUAL_MONTHLY",
            "Annual monthly",
            leaveTypeId,
            "ANNUAL",
            "Annual Leave",
            AcquisitionFrequency.MONTHLY,
            2,
            24,
            30,
            25,
            true,
            false,
            LocalDate.of(2026, 1, 1),
            null,
            true,
            null,
            null
        )));

        mockMvc.perform(get("/api/leave-acquisition-policies").with(user(UUID.randomUUID().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("ANNUAL_MONTHLY"));
    }

    @Test
    @DisplayName("POST /api/leave-acquisition-policies enforces ACQUISITION_POLICY_MANAGE")
    void createEnforcesPermission() throws Exception {
        doThrow(new AccessDeniedException("forbidden"))
            .when(permissionAuthorizationService).authorize(any(), eq("ACQUISITION_POLICY"), eq("MANAGE"));

        mockMvc.perform(post("/api/leave-acquisition-policies")
                .with(user(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "ANNUAL_MONTHLY",
                      "name": "Annual monthly",
                      "leaveTypeId": "11111111-1111-1111-1111-111111111111",
                      "frequency": "MONTHLY",
                      "monthlyRate": 2,
                      "annualQuota": 24,
                      "dayCap": 30,
                      "acquisitionDay": 25,
                      "prorataHire": true,
                      "negativeBalanceAllowed": false,
                      "startDate": "2026-01-01",
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
