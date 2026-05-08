package com.hris.leave.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.ApprovalSourceType;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.entity.ApprovalWorkflow;
import com.hris.approval.enums.StepStatus;
import com.hris.approval.enums.WorkflowStatus;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.approval.repository.ApprovalWorkflowRepository;
import com.hris.approval.service.TeamHierarchyResolver;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.InvalidWorkflowStateException;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.entity.LeaveType;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.settings.validation.entity.ValidationFallbackMode;
import com.hris.settings.validation.entity.ValidationMode;
import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.entity.ValidationWorkflow;
import com.hris.settings.validation.entity.ValidatorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveApprovalWorkflowService Unit Tests")
class LeaveApprovalWorkflowServiceTest {

    @Mock private ApprovalWorkflowRepository approvalWorkflowRepository;
    @Mock private ApprovalStepRepository approvalStepRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectAssignmentRepository projectAssignmentRepository;
    @Mock private TeamHierarchyResolver teamHierarchyResolver;
    @Mock private LeaveValidationWorkflowResolver leaveValidationWorkflowResolver;

    @InjectMocks
    private LeaveApprovalWorkflowService service;

    private Employee requester;
    private LeaveRequest leaveRequest;
    private LeaveType leaveType;
    private ValidationWorkflow workflow;

    @BeforeEach
    void setUp() {
        service = new LeaveApprovalWorkflowService(
            approvalWorkflowRepository,
            approvalStepRepository,
            employeeRepository,
            userRepository,
            projectAssignmentRepository,
            teamHierarchyResolver,
            leaveValidationWorkflowResolver,
            new ObjectMapper().findAndRegisterModules()
        );

        requester = Employee.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .build();
        leaveRequest = LeaveRequest.builder()
            .id(UUID.randomUUID())
            .employeeId(requester.getId())
            .leaveTypeId(UUID.randomUUID())
            .startDate(LocalDate.of(2026, 6, 1))
            .endDate(LocalDate.of(2026, 6, 3))
            .build();
        leaveType = LeaveType.builder()
            .id(leaveRequest.getLeaveTypeId())
            .code("ANNUAL")
            .name("Annual Leave")
            .build();
        workflow = ValidationWorkflow.builder()
            .id(UUID.randomUUID())
            .code("LEAVE_STANDARD")
            .name("Leave Standard")
            .usage(ValidationUsage.LEAVE)
            .validatorSource(ValidatorSource.TEAM_HIERARCHY)
            .validationMode(ValidationMode.ONE_REQUIRED)
            .fallbackMode(ValidationFallbackMode.HR_QUEUE)
            .active(true)
            .build();
    }

    @Test
    @DisplayName("instantiate creates workflow and deduplicated hierarchy steps")
    void instantiateCreatesWorkflowAndDeduplicatedHierarchySteps() {
        UUID managerEmployeeId = UUID.randomUUID();
        UUID headEmployeeId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID headUserId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        when(leaveValidationWorkflowResolver.resolveForLeaveType(leaveType)).thenReturn(workflow);
        when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(any(), any(), any())).thenReturn(List.of(
            ProjectAssignment.builder().teamId(teamId).build()
        ));
        when(teamHierarchyResolver.resolveAboveRequester(any(), any(), any())).thenReturn(
            new TeamHierarchyResolver.RouteCandidateList(
                List.of(
                    new TeamHierarchyResolver.RouteCandidate(managerEmployeeId, 1, headEmployeeId),
                    new TeamHierarchyResolver.RouteCandidate(headEmployeeId, 2, null),
                    new TeamHierarchyResolver.RouteCandidate(managerEmployeeId, 1, headEmployeeId)
                ),
                false
            )
        );
        when(employeeRepository.findAllById(any())).thenReturn(List.of(
            Employee.builder().id(managerEmployeeId).userId(managerUserId).build(),
            Employee.builder().id(headEmployeeId).userId(headUserId).build()
        ));
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalStepRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.instantiate(leaveRequest, requester, leaveType);

        assertThat(result.workflow().getStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);
        assertThat(result.workflow().getWorkflowCode()).isEqualTo("LEAVE_STANDARD");
        assertThat(result.steps()).hasSize(2);
        assertThat(result.steps()).extracting(ApprovalStep::getApproverId).containsExactly(managerUserId, headUserId);
        assertThat(result.steps()).extracting(ApprovalStep::getSourceType).containsOnly(ApprovalSourceType.TEAM_CHAIN);
    }

    @Test
    @DisplayName("instantiate creates informational higher-chain steps for INFO_PLUS_PRIMARY")
    void instantiateCreatesInformationalStepsForInfoPlusPrimary() {
        UUID managerEmployeeId = UUID.randomUUID();
        UUID headEmployeeId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID headUserId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        workflow.setValidationMode(ValidationMode.INFO_PLUS_PRIMARY);

        when(leaveValidationWorkflowResolver.resolveForLeaveType(leaveType)).thenReturn(workflow);
        when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(any(), any(), any())).thenReturn(List.of(
            ProjectAssignment.builder().teamId(teamId).build()
        ));
        when(teamHierarchyResolver.resolveAboveRequester(any(), any(), any())).thenReturn(
            new TeamHierarchyResolver.RouteCandidateList(
                List.of(
                    new TeamHierarchyResolver.RouteCandidate(managerEmployeeId, 1, headEmployeeId),
                    new TeamHierarchyResolver.RouteCandidate(headEmployeeId, 2, null)
                ),
                false
            )
        );
        when(employeeRepository.findAllById(any())).thenReturn(List.of(
            Employee.builder().id(managerEmployeeId).userId(managerUserId).build(),
            Employee.builder().id(headEmployeeId).userId(headUserId).build()
        ));
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalStepRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.instantiate(leaveRequest, requester, leaveType);

        assertThat(result.steps()).extracting(ApprovalStep::getStatus)
            .containsExactly(StepStatus.PENDING, StepStatus.INFORMATIONAL);
        assertThat(result.workflow().getRequiredApprovals()).isEqualTo(1);
    }

    @Test
    @DisplayName("instantiate applies fallback permission when requester is top of chain")
    void instantiateAppliesFallbackPermissionWhenNoHierarchyApproverExists() {
        UUID fallbackUserId = UUID.randomUUID();
        workflow.setFallbackMode(ValidationFallbackMode.HR_QUEUE);

        when(leaveValidationWorkflowResolver.resolveForLeaveType(leaveType)).thenReturn(workflow);
        when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(any(), any(), any())).thenReturn(List.of(
            ProjectAssignment.builder().teamId(UUID.randomUUID()).build()
        ));
        when(teamHierarchyResolver.resolveAboveRequester(any(), any(), any())).thenReturn(
            new TeamHierarchyResolver.RouteCandidateList(List.of(), true)
        );
        when(userRepository.findByPermissionNames(List.of("LEAVE_REQUEST_FALLBACK_APPROVE"))).thenReturn(List.of(
            User.builder().id(requester.getUserId()).isActive(true).build(),
            User.builder().id(fallbackUserId).isActive(true).email("fallback@example.com").build()
        ));
        when(approvalWorkflowRepository.save(any(ApprovalWorkflow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalStepRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.instantiate(leaveRequest, requester, leaveType);

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().getFirst().getApproverId()).isEqualTo(fallbackUserId);
        assertThat(result.steps().getFirst().getSourceType()).isEqualTo(ApprovalSourceType.FALLBACK);
    }

    @Test
    @DisplayName("instantiate blocks submission when no approver and fallback blocks")
    void instantiateBlocksSubmissionWhenFallbackBlocks() {
        workflow.setFallbackMode(ValidationFallbackMode.BLOCK_SUBMISSION);

        when(leaveValidationWorkflowResolver.resolveForLeaveType(leaveType)).thenReturn(workflow);
        when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.instantiate(leaveRequest, requester, leaveType))
            .isInstanceOf(InvalidWorkflowStateException.class)
            .hasMessageContaining("No hierarchy approver");
    }
}
