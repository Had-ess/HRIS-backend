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
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.repository.BonusAwardRepository;
import com.hris.compensation.repository.CompensationRecordRepository;
import com.hris.compensation.repository.PayGradeRepository;
import com.hris.organisation.repository.JobTitleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompensationAnalyticsServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private JobTitleRepository jobTitleRepository;
    @Mock private CompensationRecordRepository recordRepository;
    @Mock private PayGradeRepository payGradeRepository;
    @Mock private BonusAwardRepository awardRepository;

    @InjectMocks private CompensationAnalyticsService service;

    private final UUID deptId = UUID.randomUUID();
    private final UUID gradeId = UUID.randomUUID();

    private PayGrade gradeG3() {
        return PayGrade.builder().id(gradeId).code("G3").name("Grade 3")
            .payFrequency(PayFrequency.ANNUAL)
            .minAmount(new BigDecimal("80000")).midAmount(new BigDecimal("100000"))
            .maxAmount(new BigDecimal("120000")).build();
    }

    private Employee emp(UUID id) {
        return Employee.builder().id(id).departmentId(deptId).jobTitleId(UUID.randomUUID())
            .location("Tunis").status(EmployeeStatus.ACTIVE).build();
    }

    private CompensationRecord record(UUID empId, String base, String compa) {
        return CompensationRecord.builder().employeeId(empId).payGradeId(gradeId)
            .baseAmount(new BigDecimal(base)).currency("USD").payFrequency(PayFrequency.ANNUAL)
            .effectiveDate(LocalDate.of(2026, 1, 1)).isCurrent(true)
            .compaRatio(new BigDecimal(compa)).build();
    }

    @Test
    @DisplayName("groups by department with averages, median, band penetration, outliers, and PAID variable")
    void analytics_aggregatesByDepartment() {
        UUID e1 = UUID.randomUUID();
        UUID e2 = UUID.randomUUID();
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(emp(e1), emp(e2)));
        when(departmentRepository.findAll())
            .thenReturn(List.of(Department.builder().id(deptId).name("Engineering").build()));
        when(jobTitleRepository.findAll()).thenReturn(List.of());
        when(payGradeRepository.findAll()).thenReturn(List.of(gradeG3()));
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(e1))
            .thenReturn(Optional.of(record(e1, "100000", "1.00")));
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(e2))
            .thenReturn(Optional.of(record(e2, "130000", "1.30")));
        // e1 got a 5000 PAID bonus in 2026; e2 none.
        when(awardRepository.findByStatus(BonusAwardStatus.PAID)).thenReturn(List.of(
            BonusAward.builder().employeeId(e1).awardedAmount(new BigDecimal("5000"))
                .status(BonusAwardStatus.PAID).payoutDate(LocalDate.of(2026, 7, 1)).build()));

        CompensationAnalyticsDto dto = service.analytics(2026, GroupBy.DEPARTMENT);

        assertThat(dto.groups()).hasSize(1);
        AnalyticsGroupDto g = dto.groups().get(0);
        assertThat(g.label()).isEqualTo("Engineering");
        assertThat(g.headcount()).isEqualTo(2);
        assertThat(g.avgAnnualBase()).isEqualByComparingTo("115000.00");
        assertThat(g.medianAnnualBase()).isEqualByComparingTo("115000.00");
        assertThat(g.avgCompaRatio()).isEqualByComparingTo("1.15");
        assertThat(g.gradedCount()).isEqualTo(2);
        assertThat(g.competitive()).isEqualTo(1);     // 1.00 in [0.80,1.20]
        assertThat(g.aboveCompetitive()).isEqualTo(1); // 1.30 > 1.20
        assertThat(g.belowCompetitive()).isZero();
        assertThat(g.aboveMaxCount()).isEqualTo(1);   // 130000 > 120000 max
        assertThat(g.belowMinCount()).isZero();
        assertThat(g.totalVariablePaid()).isEqualByComparingTo("5000");
        // avg total comp = ((100000+5000) + (130000+0)) / 2 = 117500
        assertThat(g.avgTotalComp()).isEqualByComparingTo("117500.00");
    }

    @Test
    @DisplayName("excludes a PAID award paid in a different year from variable totals")
    void analytics_excludesOtherYearVariable() {
        UUID e1 = UUID.randomUUID();
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(emp(e1)));
        when(departmentRepository.findAll())
            .thenReturn(List.of(Department.builder().id(deptId).name("Engineering").build()));
        when(jobTitleRepository.findAll()).thenReturn(List.of());
        when(payGradeRepository.findAll()).thenReturn(List.of(gradeG3()));
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(e1))
            .thenReturn(Optional.of(record(e1, "100000", "1.00")));
        when(awardRepository.findByStatus(BonusAwardStatus.PAID)).thenReturn(List.of(
            BonusAward.builder().employeeId(e1).awardedAmount(new BigDecimal("9000"))
                .status(BonusAwardStatus.PAID).payoutDate(LocalDate.of(2025, 7, 1)).build()));

        CompensationAnalyticsDto dto = service.analytics(2026, GroupBy.DEPARTMENT);

        assertThat(dto.groups().get(0).totalVariablePaid()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("skips employees without a current compensation record")
    void analytics_skipsEmployeesWithoutRecord() {
        UUID e1 = UUID.randomUUID();
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(emp(e1)));
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(jobTitleRepository.findAll()).thenReturn(List.of());
        when(payGradeRepository.findAll()).thenReturn(List.of());
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(e1)).thenReturn(Optional.empty());
        when(awardRepository.findByStatus(BonusAwardStatus.PAID)).thenReturn(List.of());

        CompensationAnalyticsDto dto = service.analytics(2026, GroupBy.DEPARTMENT);

        assertThat(dto.groups()).isEmpty();
        assertThat(dto.overall().headcount()).isZero();
    }
}
