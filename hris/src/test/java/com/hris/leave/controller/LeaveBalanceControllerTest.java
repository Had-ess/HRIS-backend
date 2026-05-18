package com.hris.leave.controller;

import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.leave.dto.LeaveBalanceAdjustmentDto;
import com.hris.leave.dto.LeaveBalanceSummaryDto;
import com.hris.leave.service.LeaveBalanceLedgerService;
import com.hris.leave.service.LeaveBalanceService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LeaveBalanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, LeaveBalanceControllerTest.TestSecurityConfig.class})
class LeaveBalanceControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private LeaveBalanceService leaveBalanceService;
    @MockBean private LeaveBalanceLedgerService leaveBalanceLedgerService;
    @MockBean private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("GET /api/leave-balances returns visible balances")
    void getVisibleBalancesReturnsSummaries() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(leaveBalanceService.getVisibleBalances(any(), any(), any(), any(), any())).thenReturn(List.of(
            new LeaveBalanceSummaryDto(
                UUID.randomUUID(),
                employeeId,
                "E001",
                UUID.randomUUID(),
                "Alice",
                "Doe",
                UUID.randomUUID(),
                "ANNUAL",
                "Annual Leave",
                2026,
                20,
                3,
                2,
                0,
                15
            )
        ));

        mockMvc.perform(get("/api/leave-balances").with(TestAuthenticationFactory.jwtRequest(UUID.randomUUID(), "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].employeeCode").value("E001"))
            .andExpect(jsonPath("$.data[0].availableDays").value(15));
    }

    @Test
    @DisplayName("POST /api/leave-balances/{employeeId}/adjustments enforces LEAVE_BALANCE_MANAGE")
    void adjustBalanceEnforcesPermission() throws Exception {
        UUID employeeId = UUID.randomUUID();
        doThrow(new AccessDeniedException("forbidden"))
            .when(permissionAuthorizationService).authorizePermissionName(any(), eq("LEAVE_BALANCE_MANAGE"));

        mockMvc.perform(post("/api/leave-balances/{employeeId}/adjustments", employeeId)
                .with(TestAuthenticationFactory.jwtRequest(UUID.randomUUID(), "EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "leaveTypeId": "11111111-1111-1111-1111-111111111111",
                      "amount": 2,
                      "comment": "Manual adjustment"
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
