package com.hris.approval.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.organisation.entity.Project;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.enums.ProjectRole;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.ProjectRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectAssignmentHierarchyResolver Unit Tests")
class ProjectAssignmentHierarchyResolverTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ProjectAssignmentRepository projectAssignmentRepository;

    @Mock
    private ProjectRepository projectRepository;

    private ProjectAssignmentHierarchyResolver resolver;
    private UUID requesterId;
    private Employee requester;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        resolver = new ProjectAssignmentHierarchyResolver(
            employeeRepository,
            projectAssignmentRepository,
            projectRepository
        );
        requesterId = UUID.randomUUID();
        requester = Employee.builder()
            .id(requesterId)
            .userId(UUID.randomUUID())
            .status(EmployeeStatus.ACTIVE)
            .build();
        startDate = LocalDate.of(2026, 5, 1);
        endDate = LocalDate.of(2026, 5, 5);
    }

    @Test
    @DisplayName("resolves multi-level project chain from assignment supervisors")
    void resolvesMultiLevelProjectChainFromAssignmentSupervisors() {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        ProjectAssignment requesterAssignment = assignment(requesterId, projectId, teamId, leaderId, ProjectRole.MEMBER);
        ProjectAssignment leaderAssignment = assignment(leaderId, projectId, teamId, managerId, ProjectRole.MANAGER);

        when(employeeRepository.findById(leaderId)).thenReturn(Optional.of(activeEmployee(leaderId, leaderUserId)));
        when(employeeRepository.findById(managerId)).thenReturn(Optional.of(activeEmployee(managerId, managerUserId)));
        when(projectAssignmentRepository.findActiveAssignmentsByEmployeeIdAndProjectIdDuringPeriod(
            leaderId, projectId, startDate, endDate)).thenReturn(List.of(leaderAssignment));
        when(projectAssignmentRepository.findActiveAssignmentsByEmployeeIdAndProjectIdDuringPeriod(
            managerId, projectId, startDate, endDate)).thenReturn(List.of());

        List<ProjectAssignmentHierarchyResolver.ProjectApprover> approvers =
            resolver.resolveApprovers(requesterAssignment, requester, startDate, endDate);

        assertThat(approvers).hasSize(2);
        assertThat(approvers).extracting(ProjectAssignmentHierarchyResolver.ProjectApprover::approverUserId)
            .containsExactly(leaderUserId, managerUserId);
        assertThat(approvers.get(0).distance()).isEqualTo(1);
        assertThat(approvers.get(0).roleCode()).isEqualTo("PROJECT_N_PLUS_1");
        assertThat(approvers.get(1).distance()).isEqualTo(2);
        assertThat(approvers.get(1).roleCode()).isEqualTo("PROJECT_N_PLUS_2");
    }

    @Test
    @DisplayName("skips inactive project supervisor and continues upward")
    void skipsInactiveProjectSupervisorAndContinuesUpward() {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID inactiveLeaderId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        ProjectAssignment requesterAssignment = assignment(requesterId, projectId, teamId, inactiveLeaderId, ProjectRole.MEMBER);
        ProjectAssignment inactiveLeaderAssignment = assignment(inactiveLeaderId, projectId, teamId, managerId, ProjectRole.MANAGER);

        when(employeeRepository.findById(inactiveLeaderId)).thenReturn(Optional.of(
            Employee.builder().id(inactiveLeaderId).status(EmployeeStatus.INACTIVE).build()
        ));
        when(employeeRepository.findById(managerId)).thenReturn(Optional.of(activeEmployee(managerId, managerUserId)));
        when(projectAssignmentRepository.findActiveAssignmentsByEmployeeIdAndProjectIdDuringPeriod(
            inactiveLeaderId, projectId, startDate, endDate)).thenReturn(List.of(inactiveLeaderAssignment));
        when(projectAssignmentRepository.findActiveAssignmentsByEmployeeIdAndProjectIdDuringPeriod(
            managerId, projectId, startDate, endDate)).thenReturn(List.of());

        List<ProjectAssignmentHierarchyResolver.ProjectApprover> approvers =
            resolver.resolveApprovers(requesterAssignment, requester, startDate, endDate);

        assertThat(approvers).hasSize(1);
        assertThat(approvers.getFirst().approverUserId()).isEqualTo(managerUserId);
        assertThat(approvers.getFirst().distance()).isEqualTo(2);
    }

    @Test
    @DisplayName("falls back to project manager when root assignment has no parent")
    void fallsBackToProjectManagerWhenRootAssignmentHasNoParent() {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID projectManagerId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        UUID projectManagerUserId = UUID.randomUUID();
        ProjectAssignment requesterAssignment = assignment(requesterId, projectId, teamId, leaderId, ProjectRole.MEMBER);
        ProjectAssignment leaderAssignment = assignment(leaderId, projectId, teamId, leaderId, ProjectRole.MANAGER);

        when(employeeRepository.findById(leaderId)).thenReturn(Optional.of(activeEmployee(leaderId, leaderUserId)));
        when(employeeRepository.findById(projectManagerId)).thenReturn(Optional.of(
            activeEmployee(projectManagerId, projectManagerUserId)
        ));
        when(projectAssignmentRepository.findActiveAssignmentsByEmployeeIdAndProjectIdDuringPeriod(
            leaderId, projectId, startDate, endDate)).thenReturn(List.of(leaderAssignment));
        when(projectAssignmentRepository.findActiveAssignmentsByEmployeeIdAndProjectIdDuringPeriod(
            projectManagerId, projectId, startDate, endDate)).thenReturn(List.of());
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(Project.builder()
            .id(projectId)
            .projectManagerEmployeeId(projectManagerId)
            .build()));

        List<ProjectAssignmentHierarchyResolver.ProjectApprover> approvers =
            resolver.resolveApprovers(requesterAssignment, requester, startDate, endDate);

        assertThat(approvers).hasSize(2);
        assertThat(approvers).extracting(ProjectAssignmentHierarchyResolver.ProjectApprover::approverUserId)
            .containsExactly(leaderUserId, projectManagerUserId);
        assertThat(approvers.get(1).source()).isEqualTo("PROJECT_MANAGER_DEFAULT");
    }

    @Test
    @DisplayName("stops safely when the assignment chain contains a cycle")
    void stopsSafelyWhenAssignmentChainContainsCycle() {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        ProjectAssignment requesterAssignment = assignment(requesterId, projectId, teamId, leaderId, ProjectRole.MEMBER);
        ProjectAssignment leaderAssignment = assignment(leaderId, projectId, teamId, managerId, ProjectRole.MANAGER);
        ProjectAssignment managerAssignment = assignment(managerId, projectId, null, leaderId, ProjectRole.MANAGER);

        when(employeeRepository.findById(leaderId)).thenReturn(Optional.of(activeEmployee(leaderId, leaderUserId)));
        when(employeeRepository.findById(managerId)).thenReturn(Optional.of(activeEmployee(managerId, managerUserId)));
        when(projectAssignmentRepository.findActiveAssignmentsByEmployeeIdAndProjectIdDuringPeriod(
            leaderId, projectId, startDate, endDate)).thenReturn(List.of(leaderAssignment));
        when(projectAssignmentRepository.findActiveAssignmentsByEmployeeIdAndProjectIdDuringPeriod(
            managerId, projectId, startDate, endDate)).thenReturn(List.of(managerAssignment));

        List<ProjectAssignmentHierarchyResolver.ProjectApprover> approvers =
            resolver.resolveApprovers(requesterAssignment, requester, startDate, endDate);

        assertThat(approvers).hasSize(2);
        assertThat(approvers).extracting(ProjectAssignmentHierarchyResolver.ProjectApprover::approverUserId)
            .containsExactly(leaderUserId, managerUserId);
    }

    private ProjectAssignment assignment(
            UUID employeeId,
            UUID projectId,
            UUID teamId,
            UUID supervisorId,
            ProjectRole role) {
        return ProjectAssignment.builder()
            .employeeId(employeeId)
            .projectId(projectId)
            .teamId(teamId)
            .supervisorId(supervisorId)
            .assignmentRole(role)
            .startDate(LocalDate.of(2026, 4, 1))
            .isActive(true)
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
