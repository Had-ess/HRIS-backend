package com.hris.leave.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.leave.dto.LeaveBalanceDto;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.entity.LeavePolicy;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeavePolicyRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
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

    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> getMyBalances(UUID userId) {
        Employee employee = employeeRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        return toBalanceDtos(leaveBalanceRepository.findByEmployeeIdAndYear(
            employee.getId(), LocalDate.now().getYear()));
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> getForEmployee(UUID employeeId) {
        return toBalanceDtos(leaveBalanceRepository.findByEmployeeIdAndYear(
            employeeId, LocalDate.now().getYear()));
    }

    @Scheduled(cron = "0 0 3 1 1 *") // January 1st at 3 AM
    @Transactional
    public void rolloverBalances() {
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

            int carryOver = Math.min(
                prevBalance.getTotalDays() - prevBalance.getUsedDays(),
                policy.getCarryOverDays()
            );

            LeaveBalance newBalance = LeaveBalance.builder()
                .employeeId(prevBalance.getEmployeeId())
                .leaveTypeId(prevBalance.getLeaveTypeId())
                .year(currentYear)
                .totalDays(policy.getMaxDaysPerYear())
                .usedDays(0)
                .pendingDays(0)
                .carryOverDays(Math.max(carryOver, 0))
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
}
