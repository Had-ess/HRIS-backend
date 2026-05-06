package com.hris.approval.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeHierarchyResolver Unit Tests")
class EmployeeHierarchyResolverTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Test
    @DisplayName("resolves direct active supervisor as n+1 approver")
    void resolvesDirectActiveSupervisorAsNPlusOneApprover() {
        UUID supervisorId = UUID.randomUUID();
        UUID supervisorUserId = UUID.randomUUID();
        Employee requester = Employee.builder()
            .id(UUID.randomUUID())
            .supervisorEmployeeId(supervisorId)
            .build();
        Employee supervisor = Employee.builder()
            .id(supervisorId)
            .userId(supervisorUserId)
            .status(EmployeeStatus.ACTIVE)
            .build();

        when(employeeRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));

        EmployeeHierarchyResolver resolver = new EmployeeHierarchyResolver(
            employeeRepository,
            departmentRepository
        );

        Optional<EmployeeHierarchyResolver.HierarchyApprover> approver =
            resolver.resolveNextApprover(requester);

        assertThat(approver).isPresent();
        assertThat(approver.get().employee()).isEqualTo(supervisor);
        assertThat(approver.get().distance()).isEqualTo(1);
        assertThat(approver.get().roleCode()).isEqualTo("N_PLUS_1");
    }

    @Test
    @DisplayName("skips inactive direct supervisor and resolves next active supervisor")
    void skipsInactiveDirectSupervisorAndResolvesNextActiveSupervisor() {
        UUID inactiveSupervisorId = UUID.randomUUID();
        UUID seniorSupervisorId = UUID.randomUUID();
        UUID seniorSupervisorUserId = UUID.randomUUID();
        Employee requester = Employee.builder()
            .id(UUID.randomUUID())
            .supervisorEmployeeId(inactiveSupervisorId)
            .build();
        Employee inactiveSupervisor = Employee.builder()
            .id(inactiveSupervisorId)
            .supervisorEmployeeId(seniorSupervisorId)
            .status(EmployeeStatus.INACTIVE)
            .build();
        Employee seniorSupervisor = Employee.builder()
            .id(seniorSupervisorId)
            .userId(seniorSupervisorUserId)
            .status(EmployeeStatus.ACTIVE)
            .build();

        when(employeeRepository.findById(inactiveSupervisorId)).thenReturn(Optional.of(inactiveSupervisor));
        when(employeeRepository.findById(seniorSupervisorId)).thenReturn(Optional.of(seniorSupervisor));

        EmployeeHierarchyResolver resolver = new EmployeeHierarchyResolver(
            employeeRepository,
            departmentRepository
        );

        Optional<EmployeeHierarchyResolver.HierarchyApprover> approver =
            resolver.resolveNextApprover(requester);

        assertThat(approver).isPresent();
        assertThat(approver.get().employee()).isEqualTo(seniorSupervisor);
        assertThat(approver.get().distance()).isEqualTo(2);
        assertThat(approver.get().roleCode()).isEqualTo("N_PLUS_2");
    }

    @Test
    @DisplayName("uses department head when employee has no explicit supervisor")
    void usesDepartmentHeadWhenEmployeeHasNoExplicitSupervisor() {
        UUID departmentId = UUID.randomUUID();
        UUID headEmployeeId = UUID.randomUUID();
        UUID headUserId = UUID.randomUUID();
        Employee requester = Employee.builder()
            .id(UUID.randomUUID())
            .departmentId(departmentId)
            .build();
        Employee head = Employee.builder()
            .id(headEmployeeId)
            .userId(headUserId)
            .status(EmployeeStatus.ACTIVE)
            .build();

        when(departmentRepository.findDepartmentHead(departmentId)).thenReturn(Optional.of(head));

        EmployeeHierarchyResolver resolver = new EmployeeHierarchyResolver(
            employeeRepository,
            departmentRepository
        );

        Optional<EmployeeHierarchyResolver.HierarchyApprover> approver =
            resolver.resolveNextApprover(requester);

        assertThat(approver).isPresent();
        assertThat(approver.get().employee()).isEqualTo(head);
        assertThat(approver.get().distance()).isEqualTo(1);
        assertThat(approver.get().roleCode()).isEqualTo("DEPT_HEAD");
    }
}
