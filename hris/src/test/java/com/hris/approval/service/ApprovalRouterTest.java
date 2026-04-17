package com.hris.approval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.approval.entity.ApprovalStep;
import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.repository.ApprovalStepRepository;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.MissingDepartmentHeadException;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.enums.ProjectRole;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalRouter Unit Tests")
class ApprovalRouterTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ProjectAssignmentRepository projectAssignmentRepository;

    @Mock
    private ApprovalStepRepository approvalStepRepository;

    private ApprovalRouter approvalRouter;
    private UUID requesterId;
    private UUID workflowId;
    private UUID departmentId;

    @BeforeEach
    void setUp() {
        approvalRouter = new ApprovalRouter(
            employeeRepository,
            departmentRepository,
            projectAssignmentRepository,
            approvalStepRepository,
            new ObjectMapper()
        );
        requesterId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
        departmentId = UUID.randomUUID();

        when(employeeRepository.findById(requesterId)).thenReturn(Optional.of(Employee.builder()
            .id(requesterId)
            .userId(UUID.randomUUID())
            .departmentId(departmentId)
            .build()));
    }

    @Nested
    @DisplayName("resolveSteps()")
    class ResolveStepsTests {

        @Test
        @DisplayName("should route only to distinct project supervisors when assignments exist")
        void shouldRouteOnlyToSupervisors_WhenAssignmentsExist() {
            UUID supervisorId = UUID.randomUUID();
            UUID supervisorUserId = UUID.randomUUID();
            ProjectAssignment assignmentA = ProjectAssignment.builder()
                .employeeId(requesterId)
                .projectId(UUID.randomUUID())
                .supervisorId(supervisorId)
                .assignmentRole(ProjectRole.MEMBER)
                .startDate(LocalDate.of(2026, 4, 1))
                .build();
            ProjectAssignment assignmentB = ProjectAssignment.builder()
                .employeeId(requesterId)
                .projectId(UUID.randomUUID())
                .supervisorId(supervisorId)
                .assignmentRole(ProjectRole.MANAGER)
                .startDate(LocalDate.of(2026, 4, 1))
                .build();

            when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
                requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
                .thenReturn(List.of(assignmentA, assignmentB));
            when(approvalStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            when(employeeRepository.findById(supervisorId)).thenReturn(Optional.of(Employee.builder()
                .id(supervisorId)
                .userId(supervisorUserId)
                .build()));

            List<ApprovalStep> steps = approvalRouter.resolveSteps(
                requesterId, workflowId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

            assertThat(steps).hasSize(1);
            assertThat(steps.get(0).getApproverId()).isEqualTo(supervisorUserId);
            assertThat(steps.get(0).getContext()).isEqualTo(ApprovalContext.PROJECT);
        }

        @Test
        @DisplayName("should route only to department head when no assignments exist")
        void shouldRouteOnlyToDepartmentHead_WhenNoAssignmentsExist() {
            UUID headEmployeeId = UUID.randomUUID();
            UUID headUserId = UUID.randomUUID();

            when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
                requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
                .thenReturn(List.of());
            when(approvalStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
            when(departmentRepository.findDepartmentHead(departmentId))
                .thenReturn(Optional.of(Employee.builder()
                    .id(headEmployeeId)
                    .userId(headUserId)
                    .build()));

            List<ApprovalStep> steps = approvalRouter.resolveSteps(
                requesterId, workflowId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

            assertThat(steps).hasSize(1);
            assertThat(steps.get(0).getApproverId()).isEqualTo(headUserId);
            assertThat(steps.get(0).getContext()).isEqualTo(ApprovalContext.DEPARTMENT);
        }

        @Test
        @DisplayName("should throw when fallback department head is missing")
        void shouldThrow_WhenDepartmentHeadMissing() {
            when(projectAssignmentRepository.findActiveAssignmentsDuringPeriod(
                requesterId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
                .thenReturn(List.of());
            when(departmentRepository.findDepartmentHead(departmentId))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> approvalRouter.resolveSteps(
                requesterId, workflowId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)))
                .isInstanceOf(MissingDepartmentHeadException.class)
                .hasMessage("No department head defined for fallback approval");
        }
    }
}
