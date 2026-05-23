package com.hris.leave.controller;

import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.service.ApprovalViewService;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.leave.dto.LeaveTypeDto;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.enums.UrgencyLevel;
import com.hris.leave.dto.LeaveRequestResponseDto;
import com.hris.leave.service.LeaveTypeService;
import com.hris.leave.service.LeaveRequestService;
import com.hris.leave.service.LeaveRequestQueryService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LeaveRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    GlobalExceptionHandler.class,
    LeaveRequestControllerTest.TestSecurityConfig.class,
    LeaveRequestQueryService.class
})
class LeaveRequestControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveRequestService leaveRequestService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserProvisioningService userProvisioningService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private LeaveTypeService leaveTypeService;

    @MockBean
    private ApprovalViewService approvalViewService;

    @MockBean
    private EmployeeRepository employeeRepository;

    @MockBean
    private PermissionAuthorizationService permissionAuthorizationService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("leave detail includes requester-facing approval steps")
    void leaveDetailIncludesRequesterFacingApprovalSteps() throws Exception {
        UUID leaveId = UUID.randomUUID();
        UUID leaveTypeId = UUID.randomUUID();
        LeaveRequest request = LeaveRequest.builder()
            .id(leaveId)
            .employeeId(UUID.randomUUID())
            .leaveTypeId(leaveTypeId)
            .startDate(LocalDate.of(2026, 5, 1))
            .endDate(LocalDate.of(2026, 5, 3))
            .workingDays(2)
            .urgencyLevel(UrgencyLevel.NORMAL)
            .status(LeaveStatus.IN_APPROVAL)
            .comment("Family trip")
            .submittedAt(Instant.now())
            .build();
        LeaveRequestResponseDto responseDto = new LeaveRequestResponseDto(
            leaveId,
            request.getEmployeeId(),
            leaveTypeId,
            "ANNUAL",
            "Annual Leave",
            request.getStartDate(),
            request.getEndDate(),
            request.getWorkingDays(),
            null, null, null, null, null,
            request.getUrgencyLevel(),
            request.getStatus(),
            request.getComment(),
            request.getSubmittedAt(),
            true,
            false,
            List.of(new ApprovalStepResponseDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "LEAVE",
            leaveId,
            "LR-2026-0001",
            "John Doe",
            "Engineering",
            null,
            UUID.randomUUID(),
            "John Doe",
            1,
            StepStatus.PENDING,
            ApprovalContext.TEAM,
            "{\"role\":\"DEPT_HEAD\"}",
            null,
            null,
            null
        )));
        LeaveTypeDto leaveTypeDto = new LeaveTypeDto(leaveTypeId, "ANNUAL", "Annual Leave", true, false, true, true, null, null, null);

        when(leaveRequestService.getById(leaveId, USER_ID)).thenReturn(request);
        when(leaveTypeService.getDtoById(leaveTypeId)).thenReturn(leaveTypeDto);
        when(approvalViewService.getStepsForSubject("LEAVE", leaveId)).thenReturn(responseDto.approvalSteps());
        when(leaveRequestService.canUploadAttachment(request, USER_ID)).thenReturn(true);

        mockMvc.perform(get("/api/leave-requests/{id}", leaveId).with(TestAuthenticationFactory.jwtRequest(USER_ID, "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(leaveId.toString()))
            .andExpect(jsonPath("$.data.approvalSteps[0].approverName").value("John Doe"))
            .andExpect(jsonPath("$.data.approvalSteps[0].context").value("TEAM"))
            .andExpect(jsonPath("$.data.approvalSteps[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("leave list safely returns empty approval steps when no workflow exists")
    void leaveListSafelyReturnsEmptyApprovalStepsWhenNoWorkflowExists() throws Exception {
        UUID leaveId = UUID.randomUUID();
        UUID leaveTypeId = UUID.randomUUID();
        LeaveRequest request = LeaveRequest.builder()
            .id(leaveId)
            .employeeId(UUID.randomUUID())
            .leaveTypeId(leaveTypeId)
            .startDate(LocalDate.of(2026, 6, 1))
            .endDate(LocalDate.of(2026, 6, 2))
            .workingDays(1)
            .urgencyLevel(UrgencyLevel.NORMAL)
            .status(LeaveStatus.PENDING)
            .submittedAt(Instant.now())
            .build();
        LeaveRequestResponseDto responseDto = new LeaveRequestResponseDto(
            leaveId,
            request.getEmployeeId(),
            leaveTypeId,
            "SICK",
            "Sick Leave",
            request.getStartDate(),
            request.getEndDate(),
            request.getWorkingDays(),
            null, null, null, null, null,
            request.getUrgencyLevel(),
            request.getStatus(),
            request.getComment(),
            request.getSubmittedAt(),
            true,
            false,
            List.of()
        );
        LeaveTypeDto leaveTypeDto = new LeaveTypeDto(leaveTypeId, "SICK", "Sick Leave", true, true, true, true, null, null, null);
        var requestPage = new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1);

        when(leaveRequestService.getMyRequests(eq(USER_ID), eq(null), any()))
            .thenReturn(requestPage);
        when(leaveTypeService.getDtoById(leaveTypeId)).thenReturn(leaveTypeDto);
        when(approvalViewService.getStepsForSubjects("LEAVE", List.of(leaveId)))
            .thenReturn(Map.of(leaveId, responseDto.approvalSteps()));
        when(leaveRequestService.canUploadAttachment(request, USER_ID)).thenReturn(true);

        mockMvc.perform(get("/api/leave-requests").with(TestAuthenticationFactory.jwtRequest(USER_ID, "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].approvalSteps").isArray())
            .andExpect(jsonPath("$.data.content[0].approvalSteps").isEmpty());
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        org.springframework.security.web.SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .build();
        }
    }
}
