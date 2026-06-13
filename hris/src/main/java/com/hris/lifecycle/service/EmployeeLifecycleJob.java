package com.hris.lifecycle.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.event.SystemActor;
import com.hris.lifecycle.entity.EmployeeContract;
import com.hris.lifecycle.enums.ContractStatus;
import com.hris.lifecycle.repository.EmployeeContractRepository;
import com.hris.notification.enums.NotificationEventType;
import com.hris.tenancy.TenantJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily lifecycle sweep (EMPLOYEE_LIFECYCLE_DESIGN.md §6):
 * 1. executes scheduled terminations whose date has arrived,
 * 2. expires overdue contracts and warns HR about upcoming contract ends (30d)
 *    and probation ends (7d), deduplicated via *_notified_at stamps.
 *
 * Contract expiry never auto-terminates the employee — renewal vs termination
 * stays an HR decision.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeLifecycleJob {

    static final int CONTRACT_EXPIRY_WARNING_DAYS = 30;
    static final int PROBATION_WARNING_DAYS = 7;
    private static final List<String> HR_PERMISSIONS = List.of("EMPLOYEE_MANAGE");

    private final EmployeeRepository employeeRepository;
    private final EmployeeContractRepository contractRepository;
    private final UserRepository userRepository;
    private final EmployeeLifecycleService lifecycleService;
    private final TenantJobRunner tenantJobRunner;

    @Value("${app.lifecycle.daily.enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "${app.lifecycle.daily.cron:0 0 5 * * *}")
    @SchedulerLock(name = "employeeLifecycleJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT2M")
    public void runDailySweep() {
        if (!enabled) {
            return;
        }
        tenantJobRunner.forEachActiveTenant("employeeLifecycleJob", tenant -> {
            int terminated = executeDueTerminations();
            int transferred = executeDueTransfers();
            int contracts = sweepContracts();
            log.info("Lifecycle sweep for tenant {}: {} terminations executed, {} transfers executed, {} contract alerts",
                tenant.getSlug(), terminated, transferred, contracts);
        });
    }

    @Transactional
    public int executeDueTerminations() {
        List<Employee> due = employeeRepository.findDueScheduledTerminations(LocalDate.now());
        int count = 0;
        for (Employee employee : due) {
            try {
                // Responsibilities may have been gained since the termination was
                // scheduled — the termination stays pending until HR reassigns them.
                lifecycleService.assertNoActiveResponsibilities(employee);
                lifecycleService.executeTermination(employee, employee.getTerminationDate(),
                    "SCHEDULED_TERMINATION", SystemActor.SYSTEM_ACTOR_ID);
                count++;
            } catch (Exception e) {
                log.error("Failed to execute scheduled termination for employee {}", employee.getId(), e);
            }
        }
        return count;
    }

    @Transactional
    public int executeDueTransfers() {
        List<Employee> due = employeeRepository.findDueScheduledTransfers(LocalDate.now());
        int count = 0;
        for (Employee employee : due) {
            try {
                // executeTransfer re-validates the targets — a transfer whose
                // department was deactivated or supervisor terminated since it
                // was scheduled stays pending until HR fixes or cancels it.
                lifecycleService.executeTransfer(employee, employee.getScheduledTransferDate(),
                    employee.getScheduledTransferDepartmentId(),
                    employee.getScheduledTransferSupervisorId(),
                    SystemActor.SYSTEM_ACTOR_ID);
                count++;
            } catch (Exception e) {
                log.error("Failed to execute scheduled transfer for employee {}", employee.getId(), e);
            }
        }
        return count;
    }

    @Transactional
    public int sweepContracts() {
        LocalDate today = LocalDate.now();
        List<User> hrUsers = userRepository.findByPermissionNames(HR_PERMISSIONS);
        int alerts = 0;

        // Expired: flip status and notify once.
        for (EmployeeContract contract : contractRepository
                .findByStatusAndEndDateLessThanEqual(ContractStatus.ACTIVE, today.minusDays(1))) {
            contract.setStatus(ContractStatus.EXPIRED);
            if (contract.getExpiryNotifiedAt() == null) {
                contract.setExpiryNotifiedAt(Instant.now());
                alerts += notifyHr(hrUsers, contract,
                    NotificationEventType.CONTRACT_EXPIRED,
                    "lifecycle.contract.expired.title", "lifecycle.contract.expired.body");
            }
            contractRepository.save(contract);
        }

        // Expiring within the warning window: warn once.
        for (EmployeeContract contract : contractRepository
                .findByStatusAndEndDateLessThanEqual(ContractStatus.ACTIVE,
                    today.plusDays(CONTRACT_EXPIRY_WARNING_DAYS))) {
            if (contract.getExpiryNotifiedAt() != null) {
                continue;
            }
            contract.setExpiryNotifiedAt(Instant.now());
            contractRepository.save(contract);
            alerts += notifyHr(hrUsers, contract,
                NotificationEventType.CONTRACT_EXPIRING,
                "lifecycle.contract.expiring.title", "lifecycle.contract.expiring.body");
        }

        // Probation ending soon: warn HR + supervisor once.
        for (EmployeeContract contract : contractRepository
                .findByStatusAndProbationEndDateLessThanEqual(ContractStatus.ACTIVE,
                    today.plusDays(PROBATION_WARNING_DAYS))) {
            if (contract.getProbationNotifiedAt() != null) {
                continue;
            }
            contract.setProbationNotifiedAt(Instant.now());
            contractRepository.save(contract);
            alerts += notifyHr(hrUsers, contract,
                NotificationEventType.PROBATION_ENDING,
                "lifecycle.probation.ending.title", "lifecycle.probation.ending.body");
            alerts += notifySupervisor(contract,
                "lifecycle.probation.ending.title", "lifecycle.probation.ending.body");
        }

        return alerts;
    }

    private int notifyHr(List<User> hrUsers, EmployeeContract contract,
                         NotificationEventType eventType, String titleKey, String bodyKey) {
        Employee employee = employeeRepository.findById(contract.getEmployeeId()).orElse(null);
        if (employee == null) {
            return 0;
        }
        String date = relevantDate(eventType, contract);
        int sent = 0;
        for (User hr : hrUsers) {
            lifecycleService.publishLifecycleNotification(eventType, hr, employee,
                titleKey, bodyKey, date, contract.getContractType().name());
            sent++;
        }
        return sent;
    }

    private int notifySupervisor(EmployeeContract contract, String titleKey, String bodyKey) {
        Employee employee = employeeRepository.findById(contract.getEmployeeId()).orElse(null);
        if (employee == null || employee.getSupervisorEmployeeId() == null) {
            return 0;
        }
        return employeeRepository.findById(employee.getSupervisorEmployeeId())
            .flatMap(supervisor -> userRepository.findById(supervisor.getUserId()))
            .map(supervisorUser -> {
                lifecycleService.publishLifecycleNotification(NotificationEventType.PROBATION_ENDING,
                    supervisorUser, employee, titleKey, bodyKey,
                    String.valueOf(contract.getProbationEndDate()), contract.getContractType().name());
                return 1;
            })
            .orElse(0);
    }

    private static String relevantDate(NotificationEventType eventType, EmployeeContract contract) {
        return eventType == NotificationEventType.PROBATION_ENDING
            ? String.valueOf(contract.getProbationEndDate())
            : String.valueOf(contract.getEndDate());
    }
}
