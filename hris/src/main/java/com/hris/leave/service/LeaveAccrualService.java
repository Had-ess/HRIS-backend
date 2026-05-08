package com.hris.leave.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.leave.acquisition.entity.AcquisitionFrequency;
import com.hris.leave.acquisition.entity.LeaveAcquisitionPolicy;
import com.hris.leave.acquisition.repository.LeaveAcquisitionPolicyRepository;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveAccrualService {

    private final LeaveAcquisitionPolicyRepository policyRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceLedgerService ledgerService;
    private final AuditLogService auditLogService;

    @Value("${app.leave.accrual.enabled:false}")
    private boolean accrualEnabled;

    @Transactional(readOnly = true)
    public int calculateAccrualForPolicy(Employee employee, LeaveAcquisitionPolicy policy, LocalDate asOfDate) {
        if (!policy.isActive() || !policy.isEffectiveOn(asOfDate)) {
            return 0;
        }
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            return 0;
        }
        if (employee.getHireDate() != null && employee.getHireDate().isAfter(asOfDate)) {
            return 0;
        }
        if (employee.getTerminationDate() != null && employee.getTerminationDate().isBefore(asOfDate)) {
            return 0;
        }
        if (policy.getFrequency() != AcquisitionFrequency.MONTHLY) {
            return 0;
        }
        if (policy.getMonthlyRate() == null || policy.getMonthlyRate() == 0) {
            return 0;
        }

        int amount = policy.getMonthlyRate();
        if (policy.isProrataHire() && employee.getHireDate() != null
            && employee.getHireDate().getYear() == asOfDate.getYear()
            && employee.getHireDate().getMonth() == asOfDate.getMonth()) {
            YearMonth month = YearMonth.from(asOfDate);
            int remainingDays = Math.max(0, month.lengthOfMonth() - employee.getHireDate().getDayOfMonth() + 1);
            BigDecimal prorated = BigDecimal.valueOf(policy.getMonthlyRate())
                .multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(month.lengthOfMonth()), 0, RoundingMode.HALF_UP);
            amount = prorated.intValue();
        }
        return Math.max(amount, 0);
    }

    @Transactional
    public int applyAccrualForPolicy(Employee employee, LeaveAcquisitionPolicy policy, LocalDate asOfDate, UUID actorId) {
        LeaveType leaveType = leaveTypeRepository.findById(policy.getLeaveTypeId()).orElseThrow();
        int amount = calculateAccrualForPolicy(employee, policy, asOfDate);
        if (amount <= 0) {
            return 0;
        }

        int accruedThisYear = ledgerService.getTransactions(employee.getId()).stream()
            .filter(tx -> tx.leaveTypeId().equals(policy.getLeaveTypeId()))
            .filter(tx -> tx.type() == com.hris.leave.ledger.entity.LeaveBalanceTransactionType.ACCRUAL)
            .filter(tx -> tx.occurredAt().atZone(ZoneOffset.UTC).getYear() == asOfDate.getYear())
            .mapToInt(com.hris.leave.dto.LeaveBalanceTransactionDto::amount)
            .sum();

        if (policy.getAnnualQuota() != null) {
            amount = Math.min(amount, Math.max(policy.getAnnualQuota() - accruedThisYear, 0));
        }

        if (policy.getDayCap() != null) {
            int available = ledgerService.getAvailableBalance(employee.getId(), leaveType.getId(), asOfDate.getYear());
            amount = Math.min(amount, Math.max(policy.getDayCap() - available, 0));
        }

        if (amount <= 0) {
            return 0;
        }

        ledgerService.applyAccrual(
            employee,
            leaveType,
            asOfDate.getYear(),
            amount,
            policy.getId(),
            actorId,
            "Scheduled accrual for " + policy.getCode(),
            asOfDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        );
        auditLogService.log(actorId, AuditAction.UPDATE, "leave_accrual", policy.getId(), null,
            "Applied accrual of " + amount + " day(s) for employee " + employee.getId());
        return amount;
    }

    @Transactional
    public int runDuePolicies(LocalDate asOfDate, UUID actorId) {
        int applied = 0;
        List<LeaveAcquisitionPolicy> policies = policyRepository
            .findByIsActiveTrueAndFrequencyOrderByCodeAsc(AcquisitionFrequency.MONTHLY)
            .stream()
            .filter(policy -> policy.getAcquisitionDay() != null)
            .filter(policy -> policy.getAcquisitionDay() == Math.min(
                policy.getAcquisitionDay(),
                YearMonth.from(asOfDate).lengthOfMonth()
            ))
            .filter(policy -> asOfDate.getDayOfMonth() == Math.min(
                policy.getAcquisitionDay(),
                YearMonth.from(asOfDate).lengthOfMonth()
            ))
            .toList();

        for (LeaveAcquisitionPolicy policy : policies) {
            for (Employee employee : employeeRepository.findAll()) {
                applied += applyAccrualForPolicy(employee, policy, asOfDate, actorId);
            }
        }
        return applied;
    }

    @Scheduled(cron = "${app.leave.accrual.cron:0 0 5 * * *}")
    public void runScheduledAccrual() {
        if (!accrualEnabled) {
            return;
        }
        int count = runDuePolicies(LocalDate.now(), null);
        log.info("Applied {} leave accrual units", count);
    }
}
