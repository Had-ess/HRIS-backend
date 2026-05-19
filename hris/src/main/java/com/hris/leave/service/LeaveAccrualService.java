package com.hris.leave.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.event.ActorType;
import com.hris.common.event.SystemActor;
import com.hris.leave.accrual.entity.AccrualRunStatus;
import com.hris.leave.accrual.entity.LeaveAccrualRun;
import com.hris.leave.accrual.repository.LeaveAccrualRunRepository;
import com.hris.leave.acquisition.entity.AcquisitionFrequency;
import com.hris.leave.acquisition.entity.LeaveAcquisitionPolicy;
import com.hris.leave.acquisition.repository.LeaveAcquisitionPolicyRepository;
import com.hris.leave.dto.LeaveAccrualRunDto;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveAccrualService {

    private final LeaveAcquisitionPolicyRepository policyRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceLedgerService ledgerService;
    private final AuditLogService auditLogService;
    private final LeaveAccrualRunRepository accrualRunRepository;
    private final NotificationPublisher notificationPublisher;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.leave.accrual.enabled:false}")
    private boolean accrualEnabled;

    // --- Public query methods ---

    @Transactional(readOnly = true)
    public Page<LeaveAccrualRunDto> getRunHistory(Pageable pageable) {
        return accrualRunRepository.findAllByOrderByStartedAtDesc(pageable)
            .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<LeaveAcquisitionPolicy> findDuePolicies(LocalDate asOfDate) {
        return policyRepository
            .findByActiveTrueAndFrequencyOrderByCodeAsc(AcquisitionFrequency.MONTHLY)
            .stream()
            .filter(policy -> policy.getAcquisitionDay() != null)
            .filter(policy -> asOfDate.getDayOfMonth() == Math.min(
                policy.getAcquisitionDay(),
                YearMonth.from(asOfDate).lengthOfMonth()
            ))
            .filter(policy -> policy.isEffectiveOn(asOfDate))
            .toList();
    }

    // --- Accrual calculation ---

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

    // --- Accrual application ---

    @Transactional
    public int applyAccrualForPolicy(Employee employee, LeaveAcquisitionPolicy policy, LocalDate asOfDate, UUID actorId) {
        LeaveType leaveType = leaveTypeRepository.findById(policy.getLeaveTypeId()).orElseThrow();
        int amount = calculateAccrualForPolicy(employee, policy, asOfDate);
        if (amount <= 0) {
            return 0;
        }

        BigDecimal accruedThisYear = ledgerService.getTransactions(employee.getId()).stream()
            .filter(tx -> tx.leaveTypeId().equals(policy.getLeaveTypeId()))
            .filter(tx -> tx.type() == com.hris.leave.ledger.entity.LeaveBalanceTransactionType.ACCRUAL)
            .filter(tx -> tx.occurredAt().atZone(ZoneOffset.UTC).getYear() == asOfDate.getYear())
            .map(com.hris.leave.dto.LeaveBalanceTransactionDto::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (policy.getAnnualQuota() != null) {
            int remainingQuota = BigDecimal.valueOf(policy.getAnnualQuota())
                .subtract(accruedThisYear)
                .max(BigDecimal.ZERO)
                .intValue();
            amount = Math.min(amount, remainingQuota);
        }

        if (policy.getDayCap() != null) {
            BigDecimal available = ledgerService.getAvailableBalance(employee.getId(), leaveType.getId(), asOfDate.getYear());
            int remainingCap = BigDecimal.valueOf(policy.getDayCap())
                .subtract(available)
                .max(BigDecimal.ZERO)
                .intValue();
            amount = Math.min(amount, remainingCap);
        }

        if (amount <= 0) {
            return 0;
        }

        ledgerService.applyAccrual(
            employee,
            leaveType,
            asOfDate.getYear(),
            BigDecimal.valueOf(amount),
            policy.getId(),
            actorId,
            "Scheduled accrual for " + policy.getCode(),
            asOfDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        );
        return amount;
    }

    // --- Bulk run with formal tracking ---

    @Transactional
    public LeaveAccrualRunDto runDuePoliciesWithTracking(LocalDate asOfDate, UUID actorUserId, ActorType actorType) {
        LeaveAccrualRun run = LeaveAccrualRun.builder()
            .runDate(asOfDate)
            .startedAt(Instant.now())
            .status(AccrualRunStatus.RUNNING)
            .triggeredBy(actorType.name())
            .triggeredByUserId(actorUserId)
            .build();
        run = accrualRunRepository.save(run);

        try {
            List<LeaveAcquisitionPolicy> policies = findDuePolicies(asOfDate);
            List<Employee> employees = employeeRepository.findAll();
            int totalTransactions = 0;
            Set<UUID> processedEmployees = new HashSet<>();

            for (LeaveAcquisitionPolicy policy : policies) {
                for (Employee employee : employees) {
                    int applied = applyAccrualForPolicy(employee, policy, asOfDate,
                        actorUserId != null ? actorUserId : SystemActor.SYSTEM_ACTOR_ID);
                    if (applied > 0) {
                        totalTransactions++;
                        processedEmployees.add(employee.getId());
                    }
                }
            }

            run.markCompleted(policies.size(), processedEmployees.size(), totalTransactions);
            run = accrualRunRepository.save(run);

            // Audit the run
            UUID effectiveActorId = actorUserId != null ? actorUserId : SystemActor.SYSTEM_ACTOR_ID;
            auditLogService.log(effectiveActorId, actorType, AuditAction.ACCRUAL_RUN, "leave_accrual_run",
                run.getId(), null, run);

            // Send summary notification to HR/Admin users (not individual employees)
            publishAccrualSummaryNotification(run);

            log.info("Accrual run completed: {} policies, {} employees, {} transactions",
                run.getPoliciesProcessed(), run.getEmployeesProcessed(), run.getTransactionsCreated());

            return toDto(run);
        } catch (Exception e) {
            run.markFailed(e.getMessage());
            accrualRunRepository.save(run);
            log.error("Accrual run failed", e);
            throw e;
        }
    }

    /**
     * Legacy method preserved for backward compatibility with existing callers.
     */
    @Transactional
    public int runDuePolicies(LocalDate asOfDate, UUID actorId) {
        LeaveAccrualRunDto result = runDuePoliciesWithTracking(asOfDate, actorId, ActorType.USER);
        return result.transactionsCreated();
    }

    @Scheduled(cron = "${app.leave.accrual.cron:0 0 5 * * *}")
    @SchedulerLock(name = "leaveAccrualJob", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
    public void runScheduledAccrual() {
        if (!accrualEnabled) {
            return;
        }
        log.info("Starting scheduled accrual run");
        LeaveAccrualRunDto result = runDuePoliciesWithTracking(
            LocalDate.now(), SystemActor.SYSTEM_ACTOR_ID, ActorType.SYSTEM);
        log.info("Scheduled accrual run completed: {} transactions created", result.transactionsCreated());
    }

    // --- Notification ---

    private void publishAccrualSummaryNotification(LeaveAccrualRun run) {
        if (run.getTransactionsCreated() == 0) {
            return;
        }
        try {
            // Notify users with ACCRUAL_RUN_READ permission
            List<User> hrUsers = userRepository.findByPermissionNames(
                List.of("ACCRUAL_RUN_READ", "ACCRUAL_RUN_MANAGE"));

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("policiesProcessed", run.getPoliciesProcessed());
            params.put("transactionsCreated", run.getTransactionsCreated());
            params.put("runDate", run.getRunDate().toString());
            params.put("linkPath", "/settings/accrual-runs");
            String paramsJson = objectMapper.writeValueAsString(params);

            for (User user : hrUsers) {
                notificationPublisher.publish(NotificationEvent.builder()
                    .eventType(NotificationEventType.LEAVE_ACCRUAL_APPLIED)
                    .targetUserId(user.getId())
                    .titleKey("leave.accrual.summary.title")
                    .bodyKey("leave.accrual.summary.body")
                    .params(paramsJson)
                    .locale(user.getLocalePreference())
                    .routingKey("system.accrual.summary")
                    .publishedAt(Instant.now())
                    .build());
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize accrual summary notification params", e);
        }
    }

    // --- Mapping ---

    private LeaveAccrualRunDto toDto(LeaveAccrualRun run) {
        return new LeaveAccrualRunDto(
            run.getId(),
            run.getRunDate(),
            run.getStartedAt(),
            run.getFinishedAt(),
            run.getStatus(),
            run.getPoliciesProcessed(),
            run.getEmployeesProcessed(),
            run.getTransactionsCreated(),
            run.getErrorMessage(),
            run.getTriggeredBy(),
            run.getTriggeredByUserId()
        );
    }
}
