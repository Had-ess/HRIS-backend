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
import com.hris.auth.repository.EmployeeRepository;
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
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ProjectAssignmentRepository projectAssignmentRepository;
    @Mock private AdminUserService adminUserService;
    @Mock private AuditLogService auditLogService;
    @Mock private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock private EmployeeHistoryService employeeHistoryService;

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
        verify(adminUserService).delete(employee.getUserId(), actorId);
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
            .hasMessage("Only terminated employees can be deleted");

        verify(employeeRepository, never()).delete(employee);
        verify(adminUserService, never()).delete(employee.getUserId(), actorId);
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
        verify(adminUserService, never()).delete(employee.getUserId(), actorId);
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
            null
        ), actorId);

        verify(employeeHistoryService).recordDepartmentTransfer(any(Employee.class), any(Employee.class), eq(actorId), any(LocalDate.class));
        verify(analyticsEventPublisher).publishEmployeeTransferEvent(any(Employee.class), any(Employee.class));
        verify(employeeHistoryService, never()).recordStatusChange(any(Employee.class), any(Employee.class), eq(actorId), any(LocalDate.class), any());
    }

    @Test
    @DisplayName("records termination history and analytics when employee is terminated")
    void recordsTerminationHistoryAndAnalyticsWhenEmployeeIsTerminated() {
        UUID employeeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Employee employee = activeEmployee(employeeId, departmentId);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeMapper.toDto(any(Employee.class))).thenReturn(responseFor(employeeId, departmentId, EmployeeStatus.TERMINATED));

        employeeService.update(employeeId, new EmployeeUpdateDto(
            null,
            null,
            null,
            EmployeeStatus.TERMINATED,
            null,
            null,
            null,
            null
        ), actorId);

        verify(employeeHistoryService).recordStatusChange(any(Employee.class), any(Employee.class), eq(actorId), any(LocalDate.class), eq("TERMINATION"));
        verify(analyticsEventPublisher).publishEmployeeTerminationEvent(any(Employee.class));
        verify(employeeRepository, times(2)).save(any(Employee.class));
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
            status,
            ContractType.PERMANENT,
            departmentId,
            null,
            UUID.randomUUID(),
            null
        );
    }
}
