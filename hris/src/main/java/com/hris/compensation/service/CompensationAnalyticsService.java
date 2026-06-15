package com.hris.compensation.service;

import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.compensation.dto.CompensationAnalyticsDtos.AnalyticsGroupDto;
import com.hris.compensation.dto.CompensationAnalyticsDtos.CompensationAnalyticsDto;
import com.hris.compensation.dto.CompensationAnalyticsDtos.GroupBy;
import com.hris.compensation.entity.BonusAward;
import com.hris.compensation.entity.CompensationRecord;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.enums.BonusAwardStatus;
import com.hris.compensation.repository.BonusAwardRepository;
import com.hris.compensation.repository.CompensationRecordRepository;
import com.hris.compensation.repository.PayGradeRepository;
import com.hris.organisation.entity.JobTitle;
import com.hris.organisation.repository.JobTitleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 4 — HR compensation analytics. Read-only, computed live over the current
 * compensation records of active employees plus the year's PAID bonus awards.
 * Groups by a structural dimension (department / job family / grade / location);
 * the model has no protected attribute, so this is distribution + equity by group,
 * not a protected-class pay-gap report.
 */
@Service
@RequiredArgsConstructor
public class CompensationAnalyticsService {

    /** Competitive-range compa-ratio thresholds (standard 80%-120% of midpoint). */
    private static final BigDecimal COMPA_LOW = new BigDecimal("0.80");
    private static final BigDecimal COMPA_HIGH = new BigDecimal("1.20");
    private static final String UNSPECIFIED = "Unspecified";
    private static final String NO_GRADE = "No grade";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;
    private final CompensationRecordRepository recordRepository;
    private final PayGradeRepository payGradeRepository;
    private final BonusAwardRepository awardRepository;

    @Transactional(readOnly = true)
    public CompensationAnalyticsDto analytics(int year, GroupBy groupBy) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        Map<UUID, String> deptNames = new HashMap<>();
        departmentRepository.findAll().forEach(d -> deptNames.put(d.getId(), d.getName()));
        Map<UUID, String> jobFamilies = new HashMap<>();
        jobTitleRepository.findAll().forEach(j -> jobFamilies.put(j.getId(), j.getFamily()));
        Map<UUID, PayGrade> grades = new HashMap<>();
        payGradeRepository.findAll().forEach(g -> grades.put(g.getId(), g));

        // PAID variable pay for the year, summed per employee.
        Map<UUID, BigDecimal> variableByEmployee = new HashMap<>();
        for (BonusAward a : awardRepository.findByStatus(BonusAwardStatus.PAID)) {
            if (a.getPayoutDate() == null
                || a.getPayoutDate().isBefore(yearStart) || a.getPayoutDate().isAfter(yearEnd)
                || a.getAwardedAmount() == null) {
                continue;
            }
            variableByEmployee.merge(a.getEmployeeId(), a.getAwardedAmount(), BigDecimal::add);
        }

        Map<String, Accumulator> groupsByLabel = new LinkedHashMap<>();
        Accumulator overall = new Accumulator();

        for (Employee e : employeeRepository.findByStatus(EmployeeStatus.ACTIVE)) {
            CompensationRecord rec =
                recordRepository.findByEmployeeIdAndIsCurrentTrue(e.getId()).orElse(null);
            if (rec == null) {
                continue; // not part of the compensation population
            }
            PayGrade grade = rec.getPayGradeId() == null ? null : grades.get(rec.getPayGradeId());
            BigDecimal annualBase = CompensationService.annualize(rec.getBaseAmount(), rec.getPayFrequency());
            BigDecimal compa = rec.getCompaRatio();
            BigDecimal variable = variableByEmployee.getOrDefault(e.getId(), BigDecimal.ZERO);

            String label = switch (groupBy) {
                case DEPARTMENT -> deptNames.getOrDefault(e.getDepartmentId(), UNSPECIFIED);
                case JOB_FAMILY -> blankToUnspecified(jobFamilies.get(e.getJobTitleId()));
                case GRADE -> grade == null ? NO_GRADE : grade.getCode();
                case LOCATION -> blankToUnspecified(e.getLocation());
            };

            Accumulator acc = groupsByLabel.computeIfAbsent(label, k -> new Accumulator());
            acc.add(annualBase, compa, grade, variable);
            overall.add(annualBase, compa, grade, variable);
        }

        List<AnalyticsGroupDto> groups = groupsByLabel.entrySet().stream()
            .map(en -> en.getValue().toDto(en.getKey()))
            .sorted(Comparator.comparing(AnalyticsGroupDto::label))
            .toList();

        return new CompensationAnalyticsDto(year, groupBy, groups, overall.toDto("All"));
    }

    private static String blankToUnspecified(String value) {
        return value == null || value.isBlank() ? UNSPECIFIED : value;
    }

    /** Mutable per-group accumulator; finalized into an immutable DTO. */
    private static final class Accumulator {
        private final List<BigDecimal> bases = new ArrayList<>();
        private final List<BigDecimal> compas = new ArrayList<>();
        private final List<BigDecimal> totals = new ArrayList<>();
        private int gradedCount;
        private int belowCompetitive;
        private int competitive;
        private int aboveCompetitive;
        private int belowMin;
        private int aboveMax;
        private BigDecimal totalVariable = BigDecimal.ZERO;

        void add(BigDecimal annualBase, BigDecimal compa, PayGrade grade, BigDecimal variable) {
            bases.add(annualBase);
            totals.add(annualBase.add(variable));
            totalVariable = totalVariable.add(variable);
            if (compa != null) {
                compas.add(compa);
                if (compa.compareTo(COMPA_LOW) < 0) {
                    belowCompetitive++;
                } else if (compa.compareTo(COMPA_HIGH) > 0) {
                    aboveCompetitive++;
                } else {
                    competitive++;
                }
            }
            if (grade != null) {
                gradedCount++;
                BigDecimal min = CompensationService.annualize(grade.getMinAmount(), grade.getPayFrequency());
                BigDecimal max = CompensationService.annualize(grade.getMaxAmount(), grade.getPayFrequency());
                if (min != null && annualBase.compareTo(min) < 0) {
                    belowMin++;
                }
                if (max != null && annualBase.compareTo(max) > 0) {
                    aboveMax++;
                }
            }
        }

        AnalyticsGroupDto toDto(String label) {
            return new AnalyticsGroupDto(
                label,
                bases.size(),
                average(bases),
                median(bases),
                average(compas),
                gradedCount,
                belowCompetitive,
                competitive,
                aboveCompetitive,
                belowMin,
                aboveMax,
                totalVariable,
                average(totals));
        }
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        int mid = n / 2;
        if (n % 2 == 1) {
            return sorted.get(mid).setScale(2, RoundingMode.HALF_UP);
        }
        return sorted.get(mid - 1).add(sorted.get(mid))
            .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}
