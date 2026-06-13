package com.hris.auth.service;

import com.hris.analytics.service.AnalyticsEventPublisher;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.EmployeeResponseDto;
import com.hris.auth.dto.EmployeeUpdateDto;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.mapper.EmployeeMapper;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeDepartmentHistoryRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.EmployeeStatusHistoryRepository;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeavePolicyRepository;
import com.hris.leave.repository.LeaveRequestRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeMapper employeeMapper;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private LeavePolicyRepository leavePolicyRepository;
    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private EmployeeDepartmentHistoryRepository employeeDepartmentHistoryRepository;
    @Mock private EmployeeStatusHistoryRepository employeeStatusHistoryRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ProjectAssignmentRepository projectAssignmentRepository;
    @Mock private UserDeletionService userDeletionService;
    @Mock private AuditLogService auditLogService;
    @Mock private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock private EmployeeHistoryService employeeHistoryService;
    @Mock private com.hris.security.service.AccessScopeService accessScopeService;
    @Mock private com.hris.auth.repository.UserRepository userRepository;
    @Mock private com.hris.organisation.repository.JobTitleRepository jobTitleRepository;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    @DisplayName("deletes terminated employee without dependent records")
    void deletesTerminatedEmployeeWithoutDependencies() {
        UUID employeeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Employee employee = terminatedEmployee(employeeId);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(departmentRepository.existsByHeadEmployeeId(employeeId)).thenReturn(false);
        when(projectAssignmentRepository.existsByEmployeeId(employeeId)).thenReturn(false);
        when(projectAssignmentRepository.existsBySupervisorId(employeeId)).thenReturn(false);
        when(leaveRequestRepository.existsByEmployeeId(employeeId)).thenReturn(false);

        employeeService.delete(employeeId, actorId);

        verify(leaveBalanceRepository).deleteByEmployeeId(employeeId);
        verify(employeeRepository).delete(employee);
        verify(employeeRepository).flush();
        verify(userDeletionService).deleteUser(employee.getUserId(), actorId);
    }

    @Test
    @DisplayName("blocks deleting employee unless terminated")
    void blocksDeletingEmployeeUnlessTerminated() {
        UUID employeeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Employee employee = terminatedEmployee(employeeId);
        employee.setStatus(EmployeeStatus.ACTIVE);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> employeeService.delete(employeeId, actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only deactivated or terminated employees can be deleted");

        verify(employeeRepository, never()).delete(employee);
        verify(userDeletionService, never()).deleteUser(employee.getUserId(), actorId);
    }

    @Test
    @DisplayName("blocks deleting terminated employee with linked records")
    void blocksDeletingTerminatedEmployeeWithDependencies() {
        UUID employeeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Employee employee = terminatedEmployee(employeeId);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(departmentRepository.existsByHeadEmployeeId(employeeId)).thenReturn(false);
        when(projectAssignmentRepository.existsByEmployeeId(employeeId)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.delete(employeeId, actorId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Employee cannot be deleted because they are referenced by project assignments");

        verify(employeeRepository, never()).delete(employee);
        verify(userDeletionService, never()).deleteUser(employee.getUserId(), actorId);
    }

    @Test
    @DisplayName("records department transfer history and analytics when department changes")
    void recordsDepartmentTransferHistoryAndAnalyticsWhenDepartmentChanges() {
        UUID employeeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID previousDepartmentId = UUID.randomUUID();
        UUID newDepartmentId = UUID.randomUUID();
        Employee employee = activeEmployee(employeeId, previousDepartmentId);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(departmentRepository.findById(newDepartmentId)).thenReturn(Optional.of(
            com.hris.auth.entity.Department.builder()
                .id(newDepartmentId).name("Target").code("TGT").isActive(true).build()));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeMapper.toDto(any(Employee.class))).thenReturn(responseFor(employeeId, newDepartmentId, EmployeeStatus.ACTIVE));

        employeeService.update(employeeId, new EmployeeUpdateDto(
            null,
            null,
            null,
            null,
            null,
            newDepartmentId,
            null,
            null,
            null,
            null,
            null
        ), actorId);

        verify(employeeHistoryService).recordDepartmentTransfer(any(Employee.class), any(Employee.class), eq(actorId), any(LocalDate.class));
        verify(analyticsEventPublisher).publishEmployeeTransferEvent(any(Employee.class), any(Employee.class));
        verify(employeeHistoryService, never()).recordStatusChange(any(Employee.class), any(Employee.class), eq(actorId), any(LocalDate.class), any());
    }

    @Test
    @DisplayName("rejects TERMINATED through the generic update path — lifecycle endpoint owns it")
    void rejectsTerminationThroughGenericUpdate() {
        UUID employeeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Employee employee = activeEmployee(employeeId, departmentId);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            employeeService.update(employeeId, new EmployeeUpdateDto(
                null,
                null,
                null,
                EmployeeStatus.TERMINATED,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            ), actorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("terminate");

        verify(employeeHistoryService, never()).recordStatusChange(any(Employee.class), any(Employee.class), eq(actorId), any(LocalDate.class), any());
        verify(analyticsEventPublisher, never()).publishEmployeeTerminationEvent(any(Employee.class));
    }

    @Test
    @DisplayName("rejects a terminated or inactive supervisor")
    void rejectsInactiveSupervisor() {
        UUID employeeId = UUID.randomUUID();
        UUID supervisorId = UUID.randomUUID();
        Employee supervisor = terminatedEmployee(supervisorId);
        when(employeeRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));

        assertThatThrownBy(() -> employeeService.validateSupervisorAssignment(employeeId, supervisorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active");
    }

    @Test
    @DisplayName("rejects a supervisor assignment that closes a cycle in the supervision chain")
    void rejectsSupervisorCycle() {
        UUID employeeId = UUID.randomUUID();
        UUID supervisorId = UUID.randomUUID();
        // supervisor already reports (indirectly) to the employee: A→B→employee
        UUID middleId = UUID.randomUUID();
        Employee supervisor = activeEmployee(supervisorId, UUID.randomUUID());
        supervisor.setSupervisorEmployeeId(middleId);
        Employee middle = activeEmployee(middleId, UUID.randomUUID());
        middle.setSupervisorEmployeeId(employeeId);
        when(employeeRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));
        when(employeeRepository.findById(middleId)).thenReturn(Optional.of(middle));

        assertThatThrownBy(() -> employeeService.validateSupervisorAssignment(employeeId, supervisorId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    @DisplayName("profile summary enforces the requester's department read scope")
    void profileSummaryEnforcesReadScope() {
        UUID employeeId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Employee employee = activeEmployee(employeeId, UUID.randomUUID());
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        // requester is scoped to a different department
        when(accessScopeService.resolveDepartmentDataScope(requesterId)).thenReturn(
            com.hris.access.service.AccessResolutionService.ScopeResolution.department(
                java.util.List.of(UUID.randomUUID())));

        assertThatThrownBy(() -> employeeService.getProfileSummary(employeeId, requesterId))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private Employee terminatedEmployee(UUID employeeId) {
        return Employee.builder()
            .id(employeeId)
            .userId(UUID.randomUUID())
            .employeeCode("EMP-DEL")
            .hireDate(LocalDate.of(2020, 1, 1))
            .jobTitle("Former Analyst")
            .status(EmployeeStatus.TERMINATED)
            .contractType(ContractType.PERMANENT)
            .departmentId(UUID.randomUUID())
            .workScheduleId(UUID.randomUUID())
            .build();
    }

    private Employee activeEmployee(UUID employeeId, UUID departmentId) {
        return Employee.builder()
            .id(employeeId)
            .userId(UUID.randomUUID())
            .employeeCode("EMP-ACT")
            .hireDate(LocalDate.of(2022, 1, 1))
            .jobTitle("Analyst")
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.PERMANENT)
            .departmentId(departmentId)
            .workScheduleId(UUID.randomUUID())
            .build();
    }

    private EmployeeResponseDto responseFor(UUID employeeId, UUID departmentId, EmployeeStatus status) {
        return new EmployeeResponseDto(
            employeeId,
            UUID.randomUUID(),
            "EMP-ACT",
            LocalDate.of(2022, 1, 1),
            "Analyst",
            null,
            status,
            ContractType.PERMANENT,
            departmentId,
            null,
            UUID.randomUUID(),
            null,
            null,
            null,
            null,
            null
        );
    }
}
