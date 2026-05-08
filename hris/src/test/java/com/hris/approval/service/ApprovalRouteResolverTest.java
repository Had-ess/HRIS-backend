package com.hris.approval.service;

import com.hris.approval.enums.ApprovalContext;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.MissingDepartmentHeadException;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.enums.ProjectRole;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@DisplayName("ApprovalRouteResolver Unit Tests")
class ApprovalRouteResolverTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ProjectAssignmentRepository projectAssignmentRepository;

    @Mock
    private EmployeeHierarchyResolver employeeHierarchyResolver;

    @Mock
    private ProjectAssignmentHierarchyResolver projectAssignmentHierarchyResolver;

    private ApprovalRouteResolver approvalRouteResolver;
    private UUID requesterId;
    private UUID requesterUserId;
    private UUID departmentId;
    private Employee requester;

    @BeforeEach
    void setUp() {
        approvalRouteResolver = new ApprovalRouteResolver(
            employeeRepository,
            projectAssignmentRepository,
            employeeHierarchyResolver,
            projectAssignmentHierarchyResolver
        );
        requesterId = UUID.randomUUID();
        requesterUserId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        requester = Employee.builder()
            .id(requesterId)
            .userId(requesterUserId)
            .departmentId(departmentId)
            .build();

        when(employeeRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    }

    @Test
    @DisplayName("routes through department hierarchy and generic project chain approvers")
    void routesThroughDepartmentHierarchyAndGenericProjectChainApprovers() {
        UUID departmentSupervisorUserId = UUID.randomUUID();
        UUID leaderAUserId = UUID.randomUUID();
        UUID leaderBUserId = UUID.randomUUID();
        ProjectAssignment assignmentA = projectAssignment(UUID.randomUUID(), UUID.randomUUID());
        ProjectAssignment assignmentB = projectAssignment(UUID.randomUUID(), UUID.randomUUID());

        when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(List.of(assignmentA, assignmentB));
        when(employeeHierarchyResolver.resolveNextApprover(requester)).thenReturn(Optional.of(
            new EmployeeHierarchyResolver.HierarchyApprover(
                activeEmployee(UUID.randomUUID(), departmentSupervisorUserId),
                1,
                "N_PLUS_1"
            )
        ));
        when(projectAssignmentHierarchyResolver.resolveApprovers(
            assignmentA, requester, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(List.of(
                new ProjectAssignmentHierarchyResolver.ProjectApprover(
                    leaderAUserId, 1, "PROJECT_N_PLUS_1", "ASSIGNMENT_CHAIN"
                )
            ));
        when(projectAssignmentHierarchyResolver.resolveApprovers(
            assignmentB, requester, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(List.of(
                new ProjectAssignmentHierarchyResolver.ProjectApprover(
                    leaderBUserId, 1, "PROJECT_N_PLUS_1", "ASSIGNMENT_CHAIN"
                )
            ));

        ApprovalRouteResolver.ApprovalRoutePlan plan = approvalRouteResolver.resolveRoutePlan(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

        assertThat(plan.steps()).hasSize(3);
        assertThat(plan.steps()).extracting(ApprovalRouteResolver.ApprovalRouteStep::approverId)
            .containsExactly(departmentSupervisorUserId, leaderAUserId, leaderBUserId);
        assertThat(plan.steps().get(0).context()).isEqualTo(ApprovalContext.DEPARTMENT);
        assertThat(plan.steps().get(0).routingSnapshot()).containsEntry("role", "N_PLUS_1");
        assertThat(plan.steps().get(1).context()).isEqualTo(ApprovalContext.PROJECT);
        assertThat(plan.steps().get(1).routingSnapshot()).containsEntry("role", "PROJECT_N_PLUS_1");
        assertThat(plan.steps().get(1).routingSnapshot()).containsEntry("source", "ASSIGNMENT_CHAIN");
    }

    @Test
    @DisplayName("appends deeper project chain approvers in order")
    void appendsDeeperProjectChainApproversInOrder() {
        UUID departmentSupervisorUserId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        UUID projectManagerUserId = UUID.randomUUID();
        ProjectAssignment assignment = projectAssignment(UUID.randomUUID(), UUID.randomUUID());

        when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(List.of(assignment));
        when(employeeHierarchyResolver.resolveNextApprover(requester)).thenReturn(Optional.of(
            new EmployeeHierarchyResolver.HierarchyApprover(
                activeEmployee(UUID.randomUUID(), departmentSupervisorUserId),
                1,
                "N_PLUS_1"
            )
        ));
        when(projectAssignmentHierarchyResolver.resolveApprovers(
            assignment, requester, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(List.of(
                new ProjectAssignmentHierarchyResolver.ProjectApprover(
                    leaderUserId, 1, "PROJECT_N_PLUS_1", "ASSIGNMENT_CHAIN"
                ),
                new ProjectAssignmentHierarchyResolver.ProjectApprover(
                    projectManagerUserId, 2, "PROJECT_N_PLUS_2", "PROJECT_MANAGER_DEFAULT"
                )
            ));

        ApprovalRouteResolver.ApprovalRoutePlan plan = approvalRouteResolver.resolveRoutePlan(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

        assertThat(plan.steps()).hasSize(3);
        assertThat(plan.steps()).extracting(ApprovalRouteResolver.ApprovalRouteStep::approverId)
            .containsExactly(departmentSupervisorUserId, leaderUserId, projectManagerUserId);
        assertThat(plan.steps().get(2).routingSnapshot()).containsEntry("role", "PROJECT_N_PLUS_2");
        assertThat(plan.steps().get(2).routingSnapshot()).containsEntry("source", "PROJECT_MANAGER_DEFAULT");
    }

    @Test
    @DisplayName("routes only through employee hierarchy when no project assignments exist")
    void routesOnlyThroughEmployeeHierarchyWhenNoProjectAssignmentsExist() {
        UUID headUserId = UUID.randomUUID();

        when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(List.of());
        when(employeeHierarchyResolver.resolveNextApprover(requester)).thenReturn(Optional.of(
            new EmployeeHierarchyResolver.HierarchyApprover(
                activeEmployee(UUID.randomUUID(), headUserId),
                2,
                "N_PLUS_2"
            )
        ));

        ApprovalRouteResolver.ApprovalRoutePlan plan = approvalRouteResolver.resolveRoutePlan(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().approverId()).isEqualTo(headUserId);
        assertThat(plan.steps().getFirst().context()).isEqualTo(ApprovalContext.DEPARTMENT);
        assertThat(plan.steps().getFirst().routingSnapshot()).containsEntry("role", "N_PLUS_2");
        assertThat(plan.steps().getFirst().routingSnapshot()).containsEntry("distance", "2");
    }

    @Test
    @DisplayName("deduplicates department and project approver when they are the same user")
    void deduplicatesDepartmentAndProjectApproverWhenTheyAreTheSameUser() {
        UUID sharedSupervisorId = UUID.randomUUID();
        UUID sharedSupervisorUserId = UUID.randomUUID();
        ProjectAssignment assignment = projectAssignment(sharedSupervisorId, UUID.randomUUID());

        when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(List.of(assignment));
        when(employeeHierarchyResolver.resolveNextApprover(requester)).thenReturn(Optional.of(
            new EmployeeHierarchyResolver.HierarchyApprover(
                activeEmployee(sharedSupervisorId, sharedSupervisorUserId),
                1,
                "N_PLUS_1"
            )
        ));
        when(projectAssignmentHierarchyResolver.resolveApprovers(
            assignment, requester, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(List.of(
                new ProjectAssignmentHierarchyResolver.ProjectApprover(
                    sharedSupervisorUserId, 1, "PROJECT_N_PLUS_1", "ASSIGNMENT_CHAIN"
                )
            ));

        ApprovalRouteResolver.ApprovalRoutePlan plan = approvalRouteResolver.resolveRoutePlan(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().approverId()).isEqualTo(sharedSupervisorUserId);
        assertThat(plan.steps().getFirst().context()).isEqualTo(ApprovalContext.DEPARTMENT);
    }

    @Test
    @DisplayName("throws when neither hierarchy nor project approver can be resolved")
    void throwsWhenNeitherHierarchyNorProjectApproverCanBeResolved() {
        when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .thenReturn(List.of());
        when(employeeHierarchyResolver.resolveNextApprover(any(Employee.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalRouteResolver.resolveRoutePlan(
            requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
            .isInstanceOf(MissingDepartmentHeadException.class)
            .hasMessage("No approval supervisor could be resolved");
    }

    private ProjectAssignment projectAssignment(UUID supervisorId, UUID teamId) {
        return ProjectAssignment.builder()
            .employeeId(requesterId)
            .projectId(UUID.randomUUID())
            .teamId(teamId)
            .supervisorId(supervisorId)
            .assignmentRole(ProjectRole.MEMBER)
            .startDate(LocalDate.of(2026, 4, 1))
            .build();
    }

    private Employee activeEmployee(UUID employeeId, UUID userId) {
        return Employee.builder()
            .id(employeeId)
            .userId(userId)
            .status(EmployeeStatus.ACTIVE)
            .build();
    }

}
