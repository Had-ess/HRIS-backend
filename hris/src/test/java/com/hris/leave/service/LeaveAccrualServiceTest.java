package com.hris.leave.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.leave.acquisition.entity.AcquisitionFrequency;
import com.hris.leave.acquisition.entity.LeaveAcquisitionPolicy;
import com.hris.leave.acquisition.repository.LeaveAcquisitionPolicyRepository;
import com.hris.leave.dto.LeaveBalanceTransactionDto;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.ledger.entity.LeaveBalanceTransactionSourceType;
import com.hris.leave.ledger.entity.LeaveBalanceTransactionType;
import com.hris.leave.repository.LeaveTypeRepository;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveAccrualService Unit Tests")
class LeaveAccrualServiceTest {

    @Mock private LeaveAcquisitionPolicyRepository policyRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private LeaveBalanceLedgerService ledgerService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private LeaveAccrualService service;

    @Test
    @DisplayName("calculateAccrualForPolicy applies prorata hire")
    void calculateAccrualForPolicyAppliesProrataHire() {
        Employee employee = Employee.builder()
            .id(UUID.randomUUID())
            .status(EmployeeStatus.ACTIVE)
            .hireDate(LocalDate.of(2026, 6, 16))
            .build();
        LeaveAcquisitionPolicy policy = LeaveAcquisitionPolicy.builder()
            .id(UUID.randomUUID())
            .leaveTypeId(UUID.randomUUID())
            .frequency(AcquisitionFrequency.MONTHLY)
            .monthlyRate(2)
            .acquisitionDay(25)
            .prorataHire(true)
            .startDate(LocalDate.of(2026, 1, 1))
            .active(true)
            .build();

        int result = service.calculateAccrualForPolicy(employee, policy, LocalDate.of(2026, 6, 25));

        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("applyAccrualForPolicy respects annual quota and day cap")
    void applyAccrualForPolicyRespectsAnnualQuotaAndDayCap() {
        UUID leaveTypeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Employee employee = Employee.builder()
            .id(UUID.randomUUID())
            .status(EmployeeStatus.ACTIVE)
            .hireDate(LocalDate.of(2020, 1, 1))
            .build();
        LeaveAcquisitionPolicy policy = LeaveAcquisitionPolicy.builder()
            .id(UUID.randomUUID())
            .code("ANNUAL_MONTHLY")
            .leaveTypeId(leaveTypeId)
            .frequency(AcquisitionFrequency.MONTHLY)
            .monthlyRate(5)
            .annualQuota(12)
            .dayCap(10)
            .acquisitionDay(25)
            .prorataHire(false)
            .startDate(LocalDate.of(2026, 1, 1))
            .active(true)
            .build();
        when(leaveTypeRepository.findById(leaveTypeId)).thenReturn(Optional.of(LeaveType.builder().id(leaveTypeId).build()));
        when(ledgerService.getTransactions(employee.getId())).thenReturn(List.of(
            new LeaveBalanceTransactionDto(
                UUID.randomUUID(),
                employee.getId(),
                leaveTypeId,
                LeaveBalanceTransactionType.ACCRUAL,
                java.math.BigDecimal.valueOf(10),
                java.math.BigDecimal.valueOf(10),
                LeaveBalanceTransactionSourceType.ACQUISITION_POLICY,
                policy.getId(),
                "Earlier accrual",
                actorId,
                Instant.parse("2026-02-25T00:00:00Z")
            )
        ));
        when(ledgerService.getAvailableBalance(employee.getId(), leaveTypeId, 2026)).thenReturn(java.math.BigDecimal.valueOf(9));

        int applied = service.applyAccrualForPolicy(employee, policy, LocalDate.of(2026, 6, 25), actorId);

        assertThat(applied).isEqualTo(1);
        verify(ledgerService).applyAccrual(
            eq(employee),
            any(LeaveType.class),
            eq(2026),
            eq(java.math.BigDecimal.valueOf(1)),
            eq(policy.getId()),
            eq(actorId),
            eq("Scheduled accrual for ANNUAL_MONTHLY"),
            any(Instant.class)
        );
    }
}
