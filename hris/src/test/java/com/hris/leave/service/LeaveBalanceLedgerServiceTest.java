package com.hris.leave.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.leave.dto.LeaveBalanceAdjustmentDto;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.ledger.entity.LeaveBalanceTransaction;
import com.hris.leave.ledger.entity.LeaveBalanceTransactionType;
import com.hris.leave.ledger.repository.LeaveBalanceTransactionRepository;
import com.hris.leave.acquisition.entity.AcquisitionFrequency;
import com.hris.leave.acquisition.entity.LeaveAcquisitionPolicy;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.notification.service.TransactionalNotificationPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveBalanceLedgerService Unit Tests")
class LeaveBalanceLedgerServiceTest {

    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private LeaveBalanceTransactionRepository transactionRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private LeaveAcquisitionPolicyService leaveAcquisitionPolicyService;
    @Mock private AuditLogService auditLogService;
    @Mock private TransactionalNotificationPublisher notificationPublisher;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private LeaveBalanceLedgerService service;

    @Test
    @DisplayName("manual adjustment creates ledger transaction")
    void adjustBalanceCreatesLedgerTransaction() {
        UUID employeeId = UUID.randomUUID();
        UUID leaveTypeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        LeaveType leaveType = LeaveType.builder().id(leaveTypeId).code("ANNUAL").name("Annual").build();
        LeaveBalance balance = LeaveBalance.builder()
            .id(UUID.randomUUID())
            .employeeId(employeeId)
            .leaveTypeId(leaveTypeId)
            .year(LocalDate.now().getYear())
            .totalDays(10)
            .usedDays(0)
            .pendingDays(0)
            .carryOverDays(0)
            .build();

        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYearForUpdate(employeeId, leaveTypeId, LocalDate.now().getYear()))
            .thenReturn(Optional.of(balance));
        when(transactionRepository.save(any(LeaveBalanceTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveBalance result = service.adjustBalance(
            employeeId,
            new LeaveBalanceAdjustmentDto(leaveTypeId, 3, "Manual fix"),
            actorId
        );

        assertThat(result.getTotalDays()).isEqualTo(13);
        verify(transactionRepository).save(any(LeaveBalanceTransaction.class));
    }

    @Test
    @DisplayName("reserve allows negative balance when policy permits it")
    void reserveAllowsNegativeBalanceWhenPolicyPermitsIt() {
        UUID employeeId = UUID.randomUUID();
        UUID leaveTypeId = UUID.randomUUID();
        Employee employee = Employee.builder().id(employeeId).build();
        LeaveType leaveType = LeaveType.builder().id(leaveTypeId).balanceTracked(true).build();
        LeaveRequest request = LeaveRequest.builder()
            .id(UUID.randomUUID())
            .employeeId(employeeId)
            .leaveTypeId(leaveTypeId)
            .startDate(LocalDate.of(2026, 6, 1))
            .workingDays(5)
            .build();
        LeaveBalance balance = LeaveBalance.builder()
            .employeeId(employeeId)
            .leaveTypeId(leaveTypeId)
            .year(2026)
            .totalDays(0)
            .usedDays(0)
            .pendingDays(0)
            .build();

        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYearForUpdate(employeeId, leaveTypeId, 2026))
            .thenReturn(Optional.of(balance));
        when(leaveAcquisitionPolicyService.resolveEffectivePolicy(eq(leaveTypeId), any(LocalDate.class)))
            .thenReturn(LeaveAcquisitionPolicy.builder()
                .id(UUID.randomUUID())
                .leaveTypeId(leaveTypeId)
                .frequency(AcquisitionFrequency.MONTHLY)
                .monthlyRate(2)
                .acquisitionDay(25)
                .startDate(LocalDate.of(2026, 1, 1))
                .negativeBalanceAllowed(true)
                .active(true)
                .build());
        when(transactionRepository.save(any(LeaveBalanceTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveBalance result = service.reserveForLeaveRequest(employee, leaveType, request, 3, UUID.randomUUID());

        assertThat(result.getPendingDays()).isEqualTo(3);
        assertThat(result.getAvailableDays()).isEqualTo(-3);
    }

    @Test
    @DisplayName("accrual records accrual transaction")
    void applyAccrualRecordsAccrualTransaction() {
        UUID employeeId = UUID.randomUUID();
        UUID leaveTypeId = UUID.randomUUID();
        Employee employee = Employee.builder().id(employeeId).build();
        LeaveType leaveType = LeaveType.builder().id(leaveTypeId).build();
        LeaveBalance balance = LeaveBalance.builder()
            .employeeId(employeeId)
            .leaveTypeId(leaveTypeId)
            .year(2026)
            .totalDays(5)
            .usedDays(0)
            .pendingDays(0)
            .build();

        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYearForUpdate(employeeId, leaveTypeId, 2026))
            .thenReturn(Optional.of(balance));
        when(transactionRepository.save(any(LeaveBalanceTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveBalance result = service.applyAccrual(
            employee,
            leaveType,
            2026,
            2,
            UUID.randomUUID(),
            null,
            "Monthly accrual",
            Instant.parse("2026-06-25T00:00:00Z")
        );

        assertThat(result.getTotalDays()).isEqualTo(7);
        verify(transactionRepository).save(any(LeaveBalanceTransaction.class));
    }
}
