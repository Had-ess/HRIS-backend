package com.hris.leave.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.dto.LeaveBalanceDto;
import com.hris.leave.dto.LeaveBalanceSummaryDto;
import com.hris.leave.dto.LeaveBalanceAdjustmentDto;
import com.hris.leave.dto.LeaveBalanceTransactionDto;
import com.hris.leave.acquisition.repository.LeaveAcquisitionPolicyRepository;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.entity.LeavePolicy;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeavePolicyRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AuditLogService auditLogService;
    private final AccessScopeService accessScopeService;
    private final LeaveBalanceLedgerService leaveBalanceLedgerService;
    private final LeaveAcquisitionPolicyRepository leaveAcquisitionPolicyRepository;

    /**
     * Resolves the Employee entity for the given Keycloak user ID.
     * Used by controller endpoints that operate on the current user.
     */
    @Transactional(readOnly = true)
    public com.hris.auth.entity.Employee resolveEmployeeByUserId(UUID userId) {
        return employeeRepository.findByUserId(userId)
            .orElseThrow(() -> new com.hris.common.exception.EntityNotFoundException("Employee not found for user " + userId));
    }

    public List<LeaveBalanceDto> getMyBalances(UUID userId) {
        Employee employee = employeeRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        return getBalanceDtosForEmployeeYear(employee.getId(), LocalDate.now().getYear());
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> getForEmployee(UUID employeeId, UUID requesterId) {
        Employee targetEmployee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        assertLeaveBalanceVisibility(targetEmployee, requesterId);
        return getBalanceDtosForEmployeeYear(employeeId, LocalDate.now().getYear());
    }

    private void assertLeaveBalanceVisibility(Employee targetEmployee, UUID requesterId) {
        com.hris.access.service.AccessResolutionService.ScopeResolution scope =
            accessScopeService.resolveDepartmentDataScope(requesterId);
        if (scope.isGlobal()) {
            return;
        }

        if (scope.isDepartment() && scope.departmentIds().contains(targetEmployee.getDepartmentId())) {
            return;
        }

        throw new AccessDeniedException("You are not allowed to view leave balances for this employee");
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceSummaryDto> getVisibleBalances(UUID requesterId, UUID employeeId, String query, Integer year, Pageable pageable) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        if (targetYear < 2000 || targetYear > 3000) {
            throw new IllegalArgumentException("year must be between 2000 and 3000");
        }

        if (employeeId != null) {
            Employee targetEmployee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
            assertLeaveBalanceVisibility(targetEmployee, requesterId);
        } else if (!accessScopeService.hasAnyPermissionName(requesterId,
            "LEAVE_BALANCE_READ_OWN", "LEAVE_BALANCE_READ_SCOPED", "LEAVE_BALANCE_MANAGE")) {
            throw new AccessDeniedException("You are not allowed to browse leave balances");
        }

        List<UUID> visibleEmployeeIds = resolveVisibleEmployeeIds(requesterId, employeeId);
        if (visibleEmployeeIds != null && visibleEmployeeIds.isEmpty()) {
            return List.of();
        }

        String normalizedQuery = normalizeQuery(query);

        if (visibleEmployeeIds == null) {
            return searchVisibleSummaries(targetYear, employeeId, normalizedQuery, pageable);
        }

        if (employeeId != null) {
            return searchVisibleSummaries(targetYear, employeeId, normalizedQuery, pageable);
        }

        return searchVisibleSummaries(targetYear, visibleEmployeeIds, normalizedQuery, pageable);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceTransactionDto> getTransactions(UUID employeeId, UUID requesterId) {
        Employee targetEmployee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        assertLeaveBalanceVisibility(targetEmployee, requesterId);
        return leaveBalanceLedgerService.getTransactions(employeeId);
    }

    @Transactional
    public List<LeaveBalanceDto> adjustBalance(UUID employeeId, LeaveBalanceAdjustmentDto dto, UUID requesterId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        if (!accessScopeService.hasAnyPermissionName(requesterId, "LEAVE_BALANCE_MANAGE")) {
            throw new AccessDeniedException("You are not allowed to adjust leave balances");
        }
        leaveBalanceLedgerService.adjustBalance(employee.getId(), dto, requesterId);
        return getBalanceDtosForEmployeeYear(employeeId, LocalDate.now().getYear());
    }

    private List<LeaveBalanceDto> getBalanceDtosForEmployeeYear(UUID employeeId, int year) {
        List<LeaveBalance> existingBalances = leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year);
        Map<UUID, LeaveBalance> balancesByTypeId = existingBalances.stream()
            .collect(Collectors.toMap(LeaveBalance::getLeaveTypeId, Function.identity()));

        List<LeaveType> trackedTypes = leaveTypeRepository.findByIsActiveTrue().stream()
            .filter(LeaveType::isBalanceTracked)
            .sorted((left, right) -> left.getCode().compareToIgnoreCase(right.getCode()))
            .toList();

        List<LeaveBalance> balancesToDisplay = new ArrayList<>();
        for (LeaveType leaveType : trackedTypes) {
            balancesToDisplay.add(balancesByTypeId.getOrDefault(
                leaveType.getId(),
                LeaveBalance.builder()
                    .employeeId(employeeId)
                    .leaveTypeId(leaveType.getId())
                    .year(year)
                    .totalDays(BigDecimal.ZERO)
                    .usedDays(BigDecimal.ZERO)
                    .pendingDays(BigDecimal.ZERO)
                    .carryOverDays(BigDecimal.ZERO)
                    .build()
            ));
        }

        return toBalanceDtos(balancesToDisplay);
    }

    @Scheduled(cron = "0 0 3 1 1 *") // January 1st at 3 AM
    @SchedulerLock(name = "leaveBalanceRolloverJob", lockAtMostFor = "PT2H", lockAtLeastFor = "PT5M")
    @Transactional
    public void rolloverBalances() {
        if (!leaveAcquisitionPolicyRepository.findAll().isEmpty()) {
            log.info("Skipping legacy leave balance rollover because acquisition policies are configured");
            return;
        }
        int currentYear = LocalDate.now().getYear();
        int previousYear = currentYear - 1;

        List<LeaveBalance> previousBalances = leaveBalanceRepository.findAllByYear(previousYear);

        for (LeaveBalance prevBalance : previousBalances) {
            Employee emp = employeeRepository.findById(prevBalance.getEmployeeId())
                .orElse(null);
            if (emp == null) continue;

            int seniority = Period.between(emp.getHireDate(), LocalDate.now()).getYears();
            LeavePolicy policy = leavePolicyRepository.findApplicablePolicy(
                prevBalance.getLeaveTypeId(), emp.getContractType(), seniority)
                .orElse(null);

            if (policy == null) continue;

            BigDecimal carryOver = prevBalance.getTotalDays()
                .subtract(prevBalance.getUsedDays())
                .min(BigDecimal.valueOf(policy.getCarryOverDays()));

            LeaveBalance newBalance = LeaveBalance.builder()
                .employeeId(prevBalance.getEmployeeId())
                .leaveTypeId(prevBalance.getLeaveTypeId())
                .year(currentYear)
                .totalDays(BigDecimal.valueOf(policy.getMaxDaysPerYear()))
                .usedDays(BigDecimal.ZERO)
                .pendingDays(BigDecimal.ZERO)
                .carryOverDays(carryOver.max(BigDecimal.ZERO))
                .build();

            leaveBalanceRepository.save(newBalance);
        }

        log.info("Rolled over {} leave balances to year {}", previousBalances.size(), currentYear);
        auditLogService.log(null, AuditAction.CREATE, "leave_balance_rollover",
            null, null, Map.of("year", currentYear, "count", previousBalances.size()));
    }

    private List<LeaveBalanceDto> toBalanceDtos(List<LeaveBalance> balances) {
        Map<UUID, LeaveType> leaveTypesById = leaveTypeRepository.findAllById(
                balances.stream()
                    .map(LeaveBalance::getLeaveTypeId)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(LeaveType::getId, Function.identity()));

        return balances.stream()
            .map(balance -> toBalanceDto(balance, leaveTypesById.get(balance.getLeaveTypeId())))
            .collect(Collectors.toList());
    }

    private LeaveBalanceDto toBalanceDto(LeaveBalance balance, LeaveType leaveType) {
        return new LeaveBalanceDto(
            balance.getId(),
            balance.getEmployeeId(),
            balance.getLeaveTypeId(),
            leaveType != null ? leaveType.getCode() : null,
            leaveType != null ? leaveType.getName() : null,
            balance.getYear(),
            balance.getTotalDays(),
            balance.getUsedDays(),
            balance.getPendingDays(),
            balance.getCarryOverDays(),
            balance.getAvailableDays()
        );
    }

    private String normalizeQuery(String query) {
        return query == null || query.isBlank() ? null : query.trim().toLowerCase();
    }

    private List<LeaveBalanceSummaryDto> searchVisibleSummaries(
            int year,
            UUID employeeId,
            String query,
            Pageable pageable) {
        if (query == null) {
            return leaveBalanceRepository.searchSummariesForYear(year, employeeId, pageable).getContent();
        }
        return leaveBalanceRepository.searchSummariesForYearWithQuery(year, employeeId, query, pageable).getContent();
    }

    private List<LeaveBalanceSummaryDto> searchVisibleSummaries(
            int year,
            List<UUID> employeeIds,
            String query,
            Pageable pageable) {
        if (query == null) {
            return leaveBalanceRepository.searchSummariesForYearAndEmployeeIds(year, employeeIds, pageable).getContent();
        }
        return leaveBalanceRepository.searchSummariesForYearAndEmployeeIdsWithQuery(year, employeeIds, query, pageable)
            .getContent();
    }

    private List<UUID> resolveVisibleEmployeeIds(UUID requesterId, UUID employeeId) {
        if (employeeId != null) {
            return List.of(employeeId);
        }

        com.hris.access.service.AccessResolutionService.ScopeResolution scope =
            accessScopeService.resolveDepartmentDataScope(requesterId);
        if (scope.isGlobal()) {
            return null;
        }

        if (accessScopeService.hasAnyPermissionName(requesterId, "LEAVE_BALANCE_READ_SCOPED")) {
            if (scope.isDepartment() && !scope.departmentIds().isEmpty()) {
                List<UUID> ids = new ArrayList<>();
                for (UUID departmentId : scope.departmentIds()) {
                    ids.addAll(employeeRepository.findByDepartmentId(departmentId).stream().map(Employee::getId).toList());
                }
                return ids;
            }
            Employee requesterEmployee = accessScopeService.getEmployeeOrThrow(requesterId);
            if (requesterEmployee.getDepartmentId() == null) {
                return List.of();
            }
            return employeeRepository.findByDepartmentId(requesterEmployee.getDepartmentId()).stream()
                .map(Employee::getId)
                .toList();
        }

        if (accessScopeService.hasAnyPermissionName(requesterId, "LEAVE_BALANCE_READ_OWN")) {
            return List.of(accessScopeService.getEmployeeOrThrow(requesterId).getId());
        }

        throw new AccessDeniedException("You are not allowed to browse leave balances");
    }
}
