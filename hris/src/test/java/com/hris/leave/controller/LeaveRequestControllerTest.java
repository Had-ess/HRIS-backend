package com.hris.leave.controller;

import com.hris.approval.dto.ApprovalStepResponseDto;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.service.ApprovalViewService;
import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.enums.UrgencyLevel;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.leave.service.LeaveRequestService;
import com.hris.security.JwtAuthenticationFilter;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LeaveRequestController.class)
@Import({GlobalExceptionHandler.class, LeaveRequestControllerTest.TestSecurityConfig.class})
class LeaveRequestControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveRequestService leaveRequestService;

    @MockBean
    private ApprovalViewService approvalViewService;

    @MockBean
    private LeaveTypeRepository leaveTypeRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserProvisioningService userProvisioningService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ((FilterChain) invocation.getArgument(2)).doFilter(
                invocation.getArgument(0),
                invocation.getArgument(1)
            );
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
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
        LeaveType leaveType = LeaveType.builder().id(leaveTypeId).code("ANNUAL").name("Annual Leave").build();
        ApprovalStepResponseDto approvalStep = new ApprovalStepResponseDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "LEAVE",
            leaveId,
            UUID.randomUUID(),
            "John Doe",
            1,
            StepStatus.PENDING,
            ApprovalContext.DEPARTMENT,
            "{\"role\":\"DEPT_HEAD\"}",
            null,
            null
        );

        when(leaveRequestService.getById(leaveId, USER_ID)).thenReturn(request);
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(leaveRequestService.canUploadAttachment(request, USER_ID)).thenReturn(true);
        when(approvalViewService.getStepsForSubject("LEAVE", leaveId)).thenReturn(List.of(approvalStep));

        mockMvc.perform(get("/api/leave-requests/{id}", leaveId).with(user(USER_ID.toString()).roles("EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(leaveId.toString()))
            .andExpect(jsonPath("$.data.approvalSteps[0].approverName").value("John Doe"))
            .andExpect(jsonPath("$.data.approvalSteps[0].context").value("DEPARTMENT"))
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
        LeaveType leaveType = LeaveType.builder().id(leaveTypeId).code("SICK").name("Sick Leave").build();

        when(leaveRequestService.getMyRequests(eq(USER_ID), eq(null), any()))
            .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        when(leaveTypeRepository.findAllById(List.of(leaveTypeId))).thenReturn(List.of(leaveType));
        when(leaveRequestService.canUploadAttachment(request, USER_ID)).thenReturn(true);
        when(approvalViewService.getStepsForSubjects("LEAVE", List.of(leaveId)))
            .thenReturn(Map.of(leaveId, List.of()));

        mockMvc.perform(get("/api/leave-requests").with(user(USER_ID.toString()).roles("EMPLOYEE")))
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
