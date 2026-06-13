package com.hris.lifecycle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.service.AnalyticsEventPublisher;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeDepartmentHistoryRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.EmployeeStatusHistoryRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.service.EmployeeHistoryService;
import com.hris.auth.service.EmployeeService;
import com.hris.identity.account.LocalAccountService;
import com.hris.lifecycle.dto.LifecycleDtos.LifecycleStateDto;
import com.hris.lifecycle.dto.LifecycleDtos.ReactivateRequest;
import com.hris.lifecycle.dto.LifecycleDtos.TerminateRequest;
import com.hris.lifecycle.entity.EmployeeContract;
import com.hris.lifecycle.enums.ContractStatus;
import com.hris.lifecycle.repository.EmployeeContractRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import com.hris.organisation.entity.ProjectAssignment;
import com.hris.organisation.enums.ProjectRole;
import com.hris.organisation.hierarchy.entity.TeamHierarchyRelation;
import com.hris.organisation.hierarchy.entity.TeamHierarchyStatus;
import com.hris.organisation.hierarchy.repository.TeamHierarchyRelationRepository;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import com.hris.organisation.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeLifecycleServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeContractRepository contractRepository;
    @Mock private EmployeeStatusHistoryRepository statusHistoryRepository;
    @Mock private EmployeeDepartmentHistoryRepository departmentHistoryRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ProjectAssignmentRepository projectAssignmentRepository;
    @Mock private TeamHierarchyRelationRepository teamHierarchyRelationRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeeService employeeService;
    @Mock private EmployeeHistoryService employeeHistoryService;
    @Mock private LocalAccountService localAccountService;
    @Mock private AuditLogService auditLogService;
    @Mock private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock private TransactionalNotificationPublisher notificationPublisher;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EmployeeLifecycleService service;

    private final UUID employeeId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID supervisorEmployeeId = UUID.randomUUID();
    private final UUID supervisorUserId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private Employee employee;
    private User user;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
            .id(employeeId).userId(userId).employeeCode("EMP-1")
            .hireDate(LocalDate.of(2024, 1, 1))
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .supervisorEmployeeId(supervisorEmployeeId)
            .build();
        user = User.builder().id(userId).email("e@x").firstName("Jane").lastName("Doe").build();

        lenient().when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        lenient().when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(contractRepository.findByEmployeeIdAndStatus(eq(employeeId), eq(ContractStatus.ACTIVE)))
            .thenReturn(Optional.empty());
        lenient().when(statusHistoryRepository.findByEmployeeIdOrderByRecordedAtDesc(employeeId))
            .thenReturn(List.of());
        lenient().when(departmentHistoryRepository.findByEmployeeIdOrderByRecordedAtDesc(employeeId))
            .thenReturn(List.of());
        lenient().when(contractRepository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(employeeId))
            .thenReturn(List.of());
        lenient().when(departmentRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void futureDatedTerminationOnlySchedules() {
        LocalDate future = LocalDate.now().plusDays(30);

        LifecycleStateDto state = service.terminate(employeeId,
            new TerminateRequest(future, "End of contract"), actorId);

        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(employee.getTerminationDate()).isEqualTo(future);
        assertThat(state.terminationScheduled()).isTrue();
        verify(localAccountService, never()).revokeAllSessions(any());
        verify(analyticsEventPublisher, never()).publishEmployeeTerminationEvent(any());
    }

    @Test
    void immediateTerminationExecutesAllSideEffects() {
        EmployeeContract contract = EmployeeContract.builder()
            .id(UUID.randomUUID()).employeeId(employeeId)
            .contractType(ContractType.PERMANENT).status(ContractStatus.ACTIVE)
            .startDate(LocalDate.of(2024, 1, 1)).build();
        when(contractRepository.findByEmployeeIdAndStatus(employeeId, ContractStatus.ACTIVE))
            .thenReturn(Optional.of(contract));
        Employee supervisor = Employee.builder()
            .id(supervisorEmployeeId).userId(supervisorUserId).employeeCode("SUP-1").build();
        when(employeeRepository.findById(supervisorEmployeeId)).thenReturn(Optional.of(supervisor));
        User supervisorUser = User.builder().id(supervisorUserId).email("s@x")
            .firstName("Sam").lastName("Boss").build();
        when(userRepository.findById(supervisorUserId)).thenReturn(Optional.of(supervisorUser));

        LocalDate today = LocalDate.now();
        LifecycleStateDto state = service.terminate(employeeId,
            new TerminateRequest(today, "Resignation"), actorId);

        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.TERMINATED);
        assertThat(state.status()).isEqualTo(EmployeeStatus.TERMINATED);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.TERMINATED);
        assertThat(contract.getEndDate()).isEqualTo(today);
        assertThat(user.isActive()).isFalse();
        verify(localAccountService).revokeAllSessions(user);
        verify(employeeHistoryService).recordStatusChange(any(), any(), eq(actorId), eq(today), eq("Resignation"));
        verify(analyticsEventPublisher).publishEmployeeTerminationEvent(any());

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationPublisher).publishAfterCommit(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(NotificationEventType.EMPLOYEE_TERMINATED);
        assertThat(captor.getValue().getTargetUserId()).isEqualTo(supervisorUserId);
    }

    @Test
    void terminatingAlreadyTerminatedEmployeeFails() {
        employee.setStatus(EmployeeStatus.TERMINATED);

        assertThatThrownBy(() -> service.terminate(employeeId,
            new TerminateRequest(LocalDate.now(), "x"), actorId))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void terminationBlockedWhileEmployeeStillHasResponsibilities() {
        when(departmentRepository.existsByHeadEmployeeId(employeeId)).thenReturn(true);
        when(employeeRepository.existsBySupervisorEmployeeIdAndStatusNot(employeeId, EmployeeStatus.TERMINATED))
            .thenReturn(true);
        when(teamRepository.existsBySupervisorEmployeeIdAndIsActiveTrue(employeeId)).thenReturn(true);

        assertThatThrownBy(() -> service.terminate(employeeId,
            new TerminateRequest(LocalDate.now(), "Resignation"), actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("department head")
            .hasMessageContaining("supervises other employees")
            .hasMessageContaining("teams");

        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        verify(employeeRepository, never()).save(any());
        verify(localAccountService, never()).revokeAllSessions(any());
    }

    @Test
    void schedulingTerminationIsAlsoBlockedByResponsibilities() {
        when(projectAssignmentRepository.countActiveDistinctEmployeesBySupervisorId(eq(employeeId), any()))
            .thenReturn(2L);

        assertThatThrownBy(() -> service.terminate(employeeId,
            new TerminateRequest(LocalDate.now().plusDays(30), "End of contract"), actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("project assignments");

        assertThat(employee.getTerminationDate()).isNull();
    }

    @Test
    void terminationEndsProjectAssignmentsAndHierarchyMemberships() {
        LocalDate today = LocalDate.now();
        ProjectAssignment assignment = ProjectAssignment.builder()
            .id(UUID.randomUUID()).employeeId(employeeId).projectId(UUID.randomUUID())
            .supervisorId(supervisorEmployeeId).assignmentRole(ProjectRole.MEMBER)
            .startDate(today.minusMonths(3)).isActive(true)
            .build();
        when(projectAssignmentRepository.findByEmployeeIdAndIsActiveTrue(employeeId))
            .thenReturn(List.of(assignment));
        TeamHierarchyRelation relation = TeamHierarchyRelation.builder()
            .id(UUID.randomUUID()).teamId(UUID.randomUUID())
            .responsibleEmployeeId(supervisorEmployeeId).collaboratorEmployeeId(employeeId)
            .status(TeamHierarchyStatus.ACTIVE).startDate(today.minusMonths(3))
            .build();
        when(teamHierarchyRelationRepository
            .findByCollaboratorEmployeeIdAndStatusOrderByStartDateAscTeamIdAsc(employeeId, TeamHierarchyStatus.ACTIVE))
            .thenReturn(List.of(relation));

        service.terminate(employeeId, new TerminateRequest(today, "Resignation"), actorId);

        assertThat(assignment.isActive()).isFalse();
        assertThat(assignment.getEndDate()).isEqualTo(today);
        verify(projectAssignmentRepository).save(assignment);
        assertThat(relation.getStatus()).isEqualTo(TeamHierarchyStatus.ENDED);
        assertThat(relation.getEndDate()).isEqualTo(today);
        verify(teamHierarchyRelationRepository).save(relation);
    }

    @Test
    void cancelScheduledTerminationClearsDate() {
        employee.setTerminationDate(LocalDate.now().plusDays(10));

        LifecycleStateDto state = service.cancelScheduledTermination(employeeId, actorId);

        assertThat(employee.getTerminationDate()).isNull();
        assertThat(state.terminationScheduled()).isFalse();
    }

    @Test
    void cancelWithoutScheduledTerminationFails() {
        assertThatThrownBy(() -> service.cancelScheduledTermination(employeeId, actorId))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reactivateTerminatedEmployeeRestoresAccountAndStatus() {
        employee.setStatus(EmployeeStatus.TERMINATED);
        employee.setTerminationDate(LocalDate.now().minusDays(5));
        user.setActive(false);

        LifecycleStateDto state = service.reactivate(employeeId, new ReactivateRequest("Rehired"), actorId);

        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(employee.getTerminationDate()).isNull();
        assertThat(user.isActive()).isTrue();
        assertThat(state.status()).isEqualTo(EmployeeStatus.ACTIVE);
        verify(employeeHistoryService).recordStatusChange(any(), any(), eq(actorId), any(), eq("Rehired"));
    }

    @Test
    void reactivatingActiveEmployeeFails() {
        assertThatThrownBy(() -> service.reactivate(employeeId, new ReactivateRequest("x"), actorId))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lifecycleStateEnforcesReadScope() {
        UUID requesterId = UUID.randomUUID();

        service.getLifecycleState(employeeId, requesterId);

        verify(employeeService).getById(employeeId, requesterId);
    }

    // ---- scheduled transfers (ORG_BACKBONE_DESIGN.md §6) ----

    private com.hris.auth.entity.Department activeDepartment(UUID id, String name) {
        return com.hris.auth.entity.Department.builder()
            .id(id).name(name).code(name.toUpperCase()).isActive(true).build();
    }

    @Test
    void immediateTransferMovesEmployeeAndRecordsHistory() {
        UUID previousDepartmentId = UUID.randomUUID();
        UUID newDepartmentId = UUID.randomUUID();
        UUID newSupervisorId = UUID.randomUUID();
        UUID newSupervisorUserId = UUID.randomUUID();
        employee.setDepartmentId(previousDepartmentId);
        when(departmentRepository.findById(newDepartmentId))
            .thenReturn(Optional.of(activeDepartment(newDepartmentId, "Data")));
        Employee newSupervisor = Employee.builder()
            .id(newSupervisorId).userId(newSupervisorUserId).employeeCode("SUP-2").build();
        when(employeeRepository.findById(newSupervisorId)).thenReturn(Optional.of(newSupervisor));
        when(userRepository.findById(newSupervisorUserId)).thenReturn(Optional.of(
            User.builder().id(newSupervisorUserId).email("s2@x").firstName("New").lastName("Boss").build()));

        LifecycleStateDto state = service.transfer(employeeId,
            new com.hris.lifecycle.dto.LifecycleDtos.TransferRequest(
                LocalDate.now(), newDepartmentId, newSupervisorId), actorId);

        assertThat(employee.getDepartmentId()).isEqualTo(newDepartmentId);
        assertThat(employee.getSupervisorEmployeeId()).isEqualTo(newSupervisorId);
        assertThat(employee.getScheduledTransferDate()).isNull();
        assertThat(state.scheduledTransfer()).isNull();
        verify(employeeService).validateSupervisorAssignment(employeeId, newSupervisorId);
        verify(employeeHistoryService).recordDepartmentTransfer(any(), any(), eq(actorId), eq(LocalDate.now()));
        verify(analyticsEventPublisher).publishEmployeeTransferEvent(any(), any());

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationPublisher, org.mockito.Mockito.times(2)).publishAfterCommit(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(NotificationEvent::getEventType)
            .containsOnly(NotificationEventType.EMPLOYEE_TRANSFERRED);
        assertThat(captor.getAllValues())
            .extracting(NotificationEvent::getTargetUserId)
            .containsExactlyInAnyOrder(userId, newSupervisorUserId);
    }

    @Test
    void futureDatedTransferOnlySchedules() {
        UUID newDepartmentId = UUID.randomUUID();
        LocalDate future = LocalDate.now().plusDays(15);
        employee.setDepartmentId(UUID.randomUUID());
        when(departmentRepository.findById(newDepartmentId))
            .thenReturn(Optional.of(activeDepartment(newDepartmentId, "Data")));

        LifecycleStateDto state = service.transfer(employeeId,
            new com.hris.lifecycle.dto.LifecycleDtos.TransferRequest(
                future, newDepartmentId, null), actorId);

        assertThat(employee.getScheduledTransferDate()).isEqualTo(future);
        assertThat(employee.getScheduledTransferDepartmentId()).isEqualTo(newDepartmentId);
        assertThat(employee.getDepartmentId()).isNotEqualTo(newDepartmentId);
        assertThat(state.scheduledTransfer()).isNotNull();
        assertThat(state.scheduledTransfer().departmentName()).isEqualTo("Data");
        verify(employeeHistoryService, never()).recordDepartmentTransfer(any(), any(), any(), any());
        verify(notificationPublisher, never()).publishAfterCommit(any());
    }

    @Test
    void secondScheduledTransferIsRejected() {
        UUID newDepartmentId = UUID.randomUUID();
        employee.setDepartmentId(UUID.randomUUID());
        employee.setScheduledTransferDate(LocalDate.now().plusDays(5));

        assertThatThrownBy(() -> service.transfer(employeeId,
            new com.hris.lifecycle.dto.LifecycleDtos.TransferRequest(
                LocalDate.now().plusDays(20), newDepartmentId, null), actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already scheduled");
    }

    @Test
    void transferToSameDepartmentIsRejected() {
        UUID departmentId = UUID.randomUUID();
        employee.setDepartmentId(departmentId);

        assertThatThrownBy(() -> service.transfer(employeeId,
            new com.hris.lifecycle.dto.LifecycleDtos.TransferRequest(
                LocalDate.now(), departmentId, null), actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already in the target department");
    }

    @Test
    void transferToInactiveDepartmentIsRejected() {
        UUID newDepartmentId = UUID.randomUUID();
        employee.setDepartmentId(UUID.randomUUID());
        com.hris.auth.entity.Department inactive = activeDepartment(newDepartmentId, "Old");
        inactive.setActive(false);
        when(departmentRepository.findById(newDepartmentId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.transfer(employeeId,
            new com.hris.lifecycle.dto.LifecycleDtos.TransferRequest(
                LocalDate.now(), newDepartmentId, null), actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active");
    }

    @Test
    void terminatedEmployeeCannotBeTransferred() {
        employee.setStatus(EmployeeStatus.TERMINATED);

        assertThatThrownBy(() -> service.transfer(employeeId,
            new com.hris.lifecycle.dto.LifecycleDtos.TransferRequest(
                LocalDate.now(), UUID.randomUUID(), null), actorId))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelScheduledTransferClearsAllColumns() {
        employee.setScheduledTransferDate(LocalDate.now().plusDays(5));
        employee.setScheduledTransferDepartmentId(UUID.randomUUID());
        employee.setScheduledTransferSupervisorId(UUID.randomUUID());

        LifecycleStateDto state = service.cancelScheduledTransfer(employeeId, actorId);

        assertThat(employee.getScheduledTransferDate()).isNull();
        assertThat(employee.getScheduledTransferDepartmentId()).isNull();
        assertThat(employee.getScheduledTransferSupervisorId()).isNull();
        assertThat(state.scheduledTransfer()).isNull();
    }

    @Test
    void cancelWithoutScheduledTransferFails() {
        assertThatThrownBy(() -> service.cancelScheduledTransfer(employeeId, actorId))
            .isInstanceOf(IllegalStateException.class);
    }
}
