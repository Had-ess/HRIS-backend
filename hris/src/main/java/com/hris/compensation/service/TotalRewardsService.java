package com.hris.compensation.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.service.EmployeeService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordDto;
import com.hris.compensation.dto.CompensationAnalyticsDtos.TotalRewardsDto;
import com.hris.compensation.dto.CompensationAnalyticsDtos.VariableLineDto;
import com.hris.compensation.entity.BonusAward;
import com.hris.compensation.entity.BonusPlan;
import com.hris.compensation.entity.CompensationRecord;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.enums.BonusAwardStatus;
import com.hris.compensation.repository.BonusAwardRepository;
import com.hris.compensation.repository.BonusPlanRepository;
import com.hris.compensation.repository.CompensationRecordRepository;
import com.hris.compensation.repository.PayGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 4 — consolidated total-rewards statement for one calendar year.
 * Read-only; computed live over Phase-1 compensation records and Phase-3
 * bonus awards. Variable pay counts a bonus award only once it reaches PAID
 * with a payout date inside the requested year.
 */
@Service
@RequiredArgsConstructor
public class TotalRewardsService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final CompensationRecordRepository recordRepository;
    private final BonusAwardRepository awardRepository;
    private final BonusPlanRepository planRepository;
    private final PayGradeRepository payGradeRepository;

    /** Self-view of the caller's own statement. */
    @Transactional(readOnly = true)
    public TotalRewardsDto getMine(UUID currentUserId, int year) {
        Employee employee = employeeRepository.findByUserId(currentUserId)
            .orElseThrow(() -> new EntityNotFoundException("No employee profile for the current user"));
        return build(employee, year);
    }

    /** HR view of an employee's statement. Read scope enforced by EmployeeService.getById. */
    @Transactional(readOnly = true)
    public TotalRewardsDto getForEmployee(UUID employeeId, UUID requesterId, int year) {
        employeeService.getById(employeeId, requesterId);
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        return build(employee, year);
    }

    private TotalRewardsDto build(Employee employee, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        CompensationRecord current =
            recordRepository.findByEmployeeIdAndIsCurrentTrue(employee.getId()).orElse(null);
        PayGrade currentGrade = current == null ? null : gradeOrNull(current.getPayGradeId());

        Map<UUID, PayGrade> gradeCache = new HashMap<>();
        if (currentGrade != null) {
            gradeCache.put(currentGrade.getId(), currentGrade);
        }
        List<CompensationRecordDto> baseHistory =
            recordRepository.findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employee.getId()).stream()
                .filter(r -> overlapsYear(r, yearStart, yearEnd))
                .map(r -> CompensationService.toDto(r, r.getPayGradeId() == null ? null
                    : gradeCache.computeIfAbsent(r.getPayGradeId(), this::gradeOrNull)))
                .toList();

        Map<UUID, String> planNames = new HashMap<>();
        List<VariableLineDto> variableAwards =
            awardRepository.findByEmployeeIdAndStatus(employee.getId(), BonusAwardStatus.PAID).stream()
                .filter(a -> a.getPayoutDate() != null
                    && !a.getPayoutDate().isBefore(yearStart)
                    && !a.getPayoutDate().isAfter(yearEnd))
                .sorted(Comparator.comparing(BonusAward::getPayoutDate))
                .map(a -> new VariableLineDto(
                    a.getId(),
                    a.getAwardType(),
                    planName(a.getBonusPlanId(), planNames),
                    a.getAwardedAmount(),
                    a.getCurrency(),
                    a.getPayoutDate(),
                    a.getNote()))
                .toList();

        BigDecimal totalVariable = variableAwards.stream()
            .map(VariableLineDto::amount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal annualizedBase = current == null ? BigDecimal.ZERO
            : CompensationService.annualize(current.getBaseAmount(), current.getPayFrequency());
        BigDecimal totalCash = annualizedBase.add(totalVariable);

        String name = employee.getUser() == null ? null
            : (employee.getUser().getFirstName() + " " + employee.getUser().getLastName()).trim();

        return new TotalRewardsDto(
            employee.getId(),
            name,
            year,
            current == null ? null : current.getBaseAmount(),
            current == null ? null : current.getCurrency(),
            current == null ? null : current.getPayFrequency(),
            annualizedBase,
            currentGrade == null ? null : currentGrade.getCode(),
            currentGrade == null ? null : currentGrade.getName(),
            current == null ? null : current.getCompaRatio(),
            baseHistory,
            variableAwards,
            totalVariable,
            totalCash);
    }

    /** A record is relevant to the year if its effective span overlaps [yearStart, yearEnd]. */
    private static boolean overlapsYear(CompensationRecord r, LocalDate yearStart, LocalDate yearEnd) {
        if (r.getEffectiveDate().isAfter(yearEnd)) {
            return false;
        }
        return r.getEndDate() == null || !r.getEndDate().isBefore(yearStart);
    }

    private String planName(UUID planId, Map<UUID, String> cache) {
        if (planId == null) {
            return null;
        }
        return cache.computeIfAbsent(planId, id -> planRepository.findById(id)
            .map(BonusPlan::getName).orElse(null));
    }

    private PayGrade gradeOrNull(UUID gradeId) {
        return gradeId == null ? null : payGradeRepository.findById(gradeId).orElse(null);
    }
}
