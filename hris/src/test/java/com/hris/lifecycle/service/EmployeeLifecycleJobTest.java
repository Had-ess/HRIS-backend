package com.hris.lifecycle.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.event.SystemActor;
import com.hris.lifecycle.entity.EmployeeContract;
import com.hris.lifecycle.enums.ContractStatus;
import com.hris.lifecycle.repository.EmployeeContractRepository;
import com.hris.notification.enums.NotificationEventType;
import com.hris.tenancy.TenantJobRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeLifecycleJobTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeContractRepository contractRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeeLifecycleService lifecycleService;
    @Mock private TenantJobRunner tenantJobRunner;

    @InjectMocks
    private EmployeeLifecycleJob job;

    private final UUID employeeId = UUID.randomUUID();
    private Employee employee;
    private User hrUser;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
            .id(employeeId).userId(UUID.randomUUID()).employeeCode("EMP-1")
            .hireDate(LocalDate.of(2024, 1, 1))
            .status(EmployeeStatus.ACTIVE)
            .contractType(ContractType.FIXED_TERM)
            .build();
        hrUser = User.builder().id(UUID.randomUUID()).email("hr@x")
            .firstName("Hu").lastName("Riri").build();

        lenient().when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        lenient().when(userRepository.findByPermissionNames(any())).thenReturn(List.of(hrUser));
        lenient().when(contractRepository.save(any(EmployeeContract.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(contractRepository.findByStatusAndEndDateLessThanEqual(any(), any()))
            .thenReturn(List.of());
        lenient().when(contractRepository.findByStatusAndProbationEndDateLessThanEqual(any(), any()))
            .thenReturn(List.of());
    }

    @Test
    void dueScheduledTerminationsAreExecutedAsSystemActor() {
        LocalDate due = LocalDate.now().minusDays(1);
        employee.setTerminationDate(due);
        when(employeeRepository.findDueScheduledTerminations(any())).thenReturn(List.of(employee));

        int executed = job.executeDueTerminations();

        assertThat(executed).isEqualTo(1);
        verify(lifecycleService).executeTermination(eq(employee), eq(due),
            eq("SCHEDULED_TERMINATION"), eq(SystemActor.SYSTEM_ACTOR_ID));
    }

    @Test
    void overdueContractIsExpiredAndHrNotifiedOnce() {
        EmployeeContract contract = EmployeeContract.builder()
            .id(UUID.randomUUID()).employeeId(employeeId)
            .contractType(ContractType.FIXED_TERM).status(ContractStatus.ACTIVE)
            .startDate(LocalDate.of(2025, 1, 1)).endDate(LocalDate.now().minusDays(3))
            .build();
        when(contractRepository.findByStatusAndEndDateLessThanEqual(
            eq(ContractStatus.ACTIVE), eq(LocalDate.now().minusDays(1))))
            .thenReturn(List.of(contract));

        int alerts = job.sweepContracts();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.EXPIRED);
        assertThat(contract.getExpiryNotifiedAt()).isNotNull();
        assertThat(alerts).isEqualTo(1);
        verify(lifecycleService).publishLifecycleNotification(
            eq(NotificationEventType.CONTRACT_EXPIRED), eq(hrUser), eq(employee),
            anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void expiringContractIsWarnedOnceAndDeduped() {
        EmployeeContract contract = EmployeeContract.builder()
            .id(UUID.randomUUID()).employeeId(employeeId)
            .contractType(ContractType.FIXED_TERM).status(ContractStatus.ACTIVE)
            .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.now().plusDays(10))
            .expiryNotifiedAt(Instant.now())
            .build();
        when(contractRepository.findByStatusAndEndDateLessThanEqual(
            eq(ContractStatus.ACTIVE),
            eq(LocalDate.now().plusDays(EmployeeLifecycleJob.CONTRACT_EXPIRY_WARNING_DAYS))))
            .thenReturn(List.of(contract));

        int alerts = job.sweepContracts();

        assertThat(alerts).isZero();
        verify(lifecycleService, never()).publishLifecycleNotification(
            any(), any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void probationEndingNotifiesHrAndSupervisor() {
        UUID supervisorEmployeeId = UUID.randomUUID();
        UUID supervisorUserId = UUID.randomUUID();
        employee.setSupervisorEmployeeId(supervisorEmployeeId);
        Employee supervisor = Employee.builder()
            .id(supervisorEmployeeId).userId(supervisorUserId).employeeCode("SUP-1").build();
        User supervisorUser = User.builder().id(supervisorUserId).email("s@x")
            .firstName("Sam").lastName("Boss").build();
        when(employeeRepository.findById(supervisorEmployeeId)).thenReturn(Optional.of(supervisor));
        when(userRepository.findById(supervisorUserId)).thenReturn(Optional.of(supervisorUser));

        EmployeeContract contract = EmployeeContract.builder()
            .id(UUID.randomUUID()).employeeId(employeeId)
            .contractType(ContractType.PERMANENT).status(ContractStatus.ACTIVE)
            .startDate(LocalDate.of(2026, 5, 1))
            .probationEndDate(LocalDate.now().plusDays(3))
            .build();
        when(contractRepository.findByStatusAndProbationEndDateLessThanEqual(
            eq(ContractStatus.ACTIVE),
            eq(LocalDate.now().plusDays(EmployeeLifecycleJob.PROBATION_WARNING_DAYS))))
            .thenReturn(List.of(contract));

        int alerts = job.sweepContracts();

        assertThat(contract.getProbationNotifiedAt()).isNotNull();
        assertThat(alerts).isEqualTo(2);
        verify(lifecycleService).publishLifecycleNotification(
            eq(NotificationEventType.PROBATION_ENDING), eq(hrUser), eq(employee),
            anyString(), anyString(), anyString(), anyString());
        verify(lifecycleService).publishLifecycleNotification(
            eq(NotificationEventType.PROBATION_ENDING), eq(supervisorUser), eq(employee),
            anyString(), anyString(), anyString(), anyString());
    }
}
