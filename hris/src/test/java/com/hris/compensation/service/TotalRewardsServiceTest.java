package com.hris.compensation.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.service.EmployeeService;
import com.hris.compensation.dto.CompensationAnalyticsDtos.TotalRewardsDto;
import com.hris.compensation.entity.BonusAward;
import com.hris.compensation.entity.BonusPlan;
import com.hris.compensation.entity.CompensationRecord;
import com.hris.compensation.entity.PayGrade;
import com.hris.compensation.enums.BonusAwardStatus;
import com.hris.compensation.enums.BonusAwardType;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.repository.BonusAwardRepository;
import com.hris.compensation.repository.BonusPlanRepository;
import com.hris.compensation.repository.CompensationRecordRepository;
import com.hris.compensation.repository.PayGradeRepository;
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
class TotalRewardsServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeService employeeService;
    @Mock private CompensationRecordRepository recordRepository;
    @Mock private BonusAwardRepository awardRepository;
    @Mock private BonusPlanRepository planRepository;
    @Mock private PayGradeRepository payGradeRepository;

    @InjectMocks private TotalRewardsService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();
    private final UUID gradeId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();

    private Employee employee() {
        return Employee.builder().id(empId).userId(userId)
            .user(User.builder().firstName("Yasmine").lastName("Trabelsi").build())
            .build();
    }

    private CompensationRecord current() {
        return CompensationRecord.builder().employeeId(empId).payGradeId(gradeId)
            .baseAmount(new BigDecimal("100000")).currency("USD").payFrequency(PayFrequency.ANNUAL)
            .effectiveDate(LocalDate.of(2026, 1, 1)).isCurrent(true)
            .compaRatio(new BigDecimal("1.0000")).build();
    }

    @Test
    @DisplayName("statement sums annualized base + PAID variable in the year for total cash")
    void getMine_buildsStatement() {
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee()));
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(empId)).thenReturn(Optional.of(current()));
        when(recordRepository.findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(empId))
            .thenReturn(List.of(current()));
        when(payGradeRepository.findById(gradeId)).thenReturn(Optional.of(
            PayGrade.builder().id(gradeId).code("G3").name("Grade 3")
                .payFrequency(PayFrequency.ANNUAL).build()));
        when(planRepository.findById(planId)).thenReturn(Optional.of(
            BonusPlan.builder().id(planId).code("ANNUAL_STI").name("Annual Incentive Plan").build()));
        when(awardRepository.findByEmployeeIdAndStatus(empId, BonusAwardStatus.PAID)).thenReturn(List.of(
            BonusAward.builder().id(UUID.randomUUID()).employeeId(empId).bonusPlanId(planId)
                .awardType(BonusAwardType.CYCLE).awardedAmount(new BigDecimal("8000")).currency("USD")
                .status(BonusAwardStatus.PAID).payoutDate(LocalDate.of(2026, 7, 15)).build(),
            // paid in a previous year -> excluded
            BonusAward.builder().id(UUID.randomUUID()).employeeId(empId).bonusPlanId(planId)
                .awardType(BonusAwardType.SPOT).awardedAmount(new BigDecimal("2000")).currency("USD")
                .status(BonusAwardStatus.PAID).payoutDate(LocalDate.of(2025, 3, 1)).build()));

        TotalRewardsDto dto = service.getMine(userId, 2026);

        assertThat(dto.employeeName()).isEqualTo("Yasmine Trabelsi");
        assertThat(dto.year()).isEqualTo(2026);
        assertThat(dto.annualizedBase()).isEqualByComparingTo("100000");
        assertThat(dto.payGradeCode()).isEqualTo("G3");
        assertThat(dto.variableAwards()).hasSize(1);
        assertThat(dto.variableAwards().get(0).planName()).isEqualTo("Annual Incentive Plan");
        assertThat(dto.totalVariable()).isEqualByComparingTo("8000");
        assertThat(dto.totalCashCompensation()).isEqualByComparingTo("108000");
    }

    @Test
    @DisplayName("monthly base is annualized x12 in the total")
    void getMine_annualizesMonthlyBase() {
        Employee e = employee();
        CompensationRecord monthly = CompensationRecord.builder().employeeId(empId)
            .baseAmount(new BigDecimal("5000")).currency("USD").payFrequency(PayFrequency.MONTHLY)
            .effectiveDate(LocalDate.of(2026, 1, 1)).isCurrent(true).build();
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(e));
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(empId)).thenReturn(Optional.of(monthly));
        when(recordRepository.findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(empId))
            .thenReturn(List.of(monthly));
        when(awardRepository.findByEmployeeIdAndStatus(empId, BonusAwardStatus.PAID)).thenReturn(List.of());

        TotalRewardsDto dto = service.getMine(userId, 2026);

        assertThat(dto.annualizedBase()).isEqualByComparingTo("60000");
        assertThat(dto.totalVariable()).isEqualByComparingTo("0");
        assertThat(dto.totalCashCompensation()).isEqualByComparingTo("60000");
    }
}
