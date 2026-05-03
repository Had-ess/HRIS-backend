package com.hris.auth.service;

import com.hris.analytics.service.AuditLogService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
}
