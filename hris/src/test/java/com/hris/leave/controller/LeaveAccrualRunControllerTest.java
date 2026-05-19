package com.hris.leave.controller;

import com.hris.common.GlobalExceptionHandler;
import com.hris.common.event.ActorType;
import com.hris.leave.accrual.entity.AccrualRunStatus;
import com.hris.leave.dto.LeaveAccrualRunDto;
import com.hris.leave.service.LeaveAccrualService;
import com.hris.leave.service.LeaveAcquisitionPolicyService;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.support.TestAuthenticationFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LeaveAccrualRunController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, LeaveAccrualRunControllerTest.TestSecurityConfig.class})
class LeaveAccrualRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private LeaveAccrualService leaveAccrualService;
    @MockBean private LeaveAcquisitionPolicyService leaveAcquisitionPolicyService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("run due policies uses local user id instead of Keycloak subject")
    void runDuePoliciesUsesLocalUserId() throws Exception {
        UUID localUserId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(leaveAccrualService.runDuePoliciesWithTracking(any(LocalDate.class), eq(localUserId), eq(ActorType.USER)))
            .thenReturn(new LeaveAccrualRunDto(
                runId,
                LocalDate.of(2026, 5, 19),
                Instant.parse("2026-05-19T12:00:00Z"),
                Instant.parse("2026-05-19T12:00:02Z"),
                AccrualRunStatus.COMPLETED,
                1,
                2,
                2,
                null,
                "USER",
                localUserId
            ));

        mockMvc.perform(post("/api/accrual-runs/run-due")
                .with(TestAuthenticationFactory.jwtRequest(localUserId, "HR_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(runId.toString()))
            .andExpect(jsonPath("$.data.triggeredByUserId").value(localUserId.toString()));

        verify(leaveAccrualService).runDuePoliciesWithTracking(any(LocalDate.class), eq(localUserId), eq(ActorType.USER));
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
