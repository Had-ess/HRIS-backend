package com.hris.approval.controller;

import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.service.ApprovalStepQueryService;
import com.hris.approval.service.ApprovalStepService;
import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApprovalStepController.class)
@Import({GlobalExceptionHandler.class, ApprovalStepControllerTest.TestSecurityConfig.class})
class ApprovalStepControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STEP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORKFLOW_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SUBJECT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApprovalStepService approvalStepService;
    @MockBean
    private ApprovalStepQueryService approvalStepQueryService;
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
    @DisplayName("controller requires authenticated access")
    void controllerRequiresAuthenticatedAccess() {
        PreAuthorize preAuthorize = ApprovalStepController.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    @DisplayName("pending approvals response includes workflow subject metadata")
    void pendingApprovalsResponseIncludesWorkflowSubjectMetadata() throws Exception {
        ApprovalStep step = ApprovalStep.builder()
            .id(STEP_ID)
            .workflowId(WORKFLOW_ID)
            .approverId(USER_ID)
            .stepOrder(1)
            .status(StepStatus.PENDING)
            .context(ApprovalContext.PROJECT)
            .routingSnapshot("{\"role\":\"PROJECT_SUPERVISOR\"}")
            .build();

        when(approvalStepService.getPendingForApprover(eq(USER_ID), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(step)));
        when(approvalStepQueryService.toPendingPage(any(PageImpl.class)))
            .thenReturn(new PageImpl<>(List.of(new com.hris.approval.dto.ApprovalStepResponseDto(
                STEP_ID,
                WORKFLOW_ID,
                "LEAVE",
                SUBJECT_ID,
                SUBJECT_ID.toString(),
                "Requester Name",
                "Engineering",
                null,
                USER_ID,
                "Amine Supervisor",
                1,
                StepStatus.PENDING,
                ApprovalContext.PROJECT,
                "{\"role\":\"PROJECT_SUPERVISOR\"}",
                null,
                null
            ))));

        mockMvc.perform(get("/api/approval-steps/pending").with(user(USER_ID.toString()).roles("EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].workflowId").value(WORKFLOW_ID.toString()))
            .andExpect(jsonPath("$.data.content[0].subjectType").value("LEAVE"))
            .andExpect(jsonPath("$.data.content[0].subjectId").value(SUBJECT_ID.toString()))
            .andExpect(jsonPath("$.data.content[0].approverName").value("Amine Supervisor"));
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
