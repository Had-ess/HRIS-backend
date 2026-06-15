package com.hris.compensation.service;

import com.hris.analytics.entity.PerformanceFact;
import com.hris.analytics.repository.PerformanceFactRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.compensation.dto.CompensationBonusDtos.BonusAwardDto;
import com.hris.compensation.dto.CompensationBonusDtos.BonusAwardUpdateDto;
import com.hris.compensation.dto.CompensationBonusDtos.SpotAwardCreateDto;
import com.hris.compensation.entity.BonusAward;
import com.hris.compensation.entity.BonusCycle;
import com.hris.compensation.entity.BonusPlan;
import com.hris.compensation.entity.BonusPool;
import com.hris.compensation.entity.CompensationRecord;
import com.hris.compensation.enums.BonusAwardStatus;
import com.hris.compensation.enums.BonusAwardType;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.enums.RatingBand;
import com.hris.compensation.enums.ReviewCycleStatus;
import com.hris.compensation.repository.BonusAwardRepository;
import com.hris.compensation.repository.BonusCycleDepartmentRepository;
import com.hris.compensation.repository.BonusCycleRepository;
import com.hris.compensation.repository.BonusPlanRepository;
import com.hris.compensation.repository.BonusPoolRepository;
import com.hris.compensation.repository.CompensationRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BonusCycleServiceTest {

    @Mock private BonusCycleRepository cycleRepository;
    @Mock private BonusCycleDepartmentRepository cycleDepartmentRepository;
    @Mock private BonusPoolRepository poolRepository;
    @Mock private BonusAwardRepository awardRepository;
    @Mock private BonusPlanRepository planRepository;
    @Mock private CompensationRecordRepository recordRepository;
    @Mock private PerformanceFactRepository performanceFactRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private BonusCycleService service;

    private final UUID cycleId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID managerUserId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private BonusPlan plan() {
        return BonusPlan.builder().id(planId).code("STI").name("Annual Incentive")
            .targetPercent(new BigDecimal("10.00")).isActive(true).build();
    }

    private BonusCycle cycle(ReviewCycleStatus status) {
        return BonusCycle.builder()
            .id(cycleId).name("2026 Bonus").status(status).bonusPlanId(planId)
            .sourcePerformanceCycleId(UUID.randomUUID())
            .payoutDate(LocalDate.of(2026, 9, 1))
            .companyFundingFactor(new BigDecimal("0.8000"))
            .ratingLowMax(2).ratingHighMin(4)
            .perfFactorLow(new BigDecimal("0.5000")).perfFactorSolid(new BigDecimal("1.0000"))
            .perfFactorHigh(new BigDecimal("1.2500"))
            .build();
    }

    private BonusAward award(BonusAwardStatus status) {
        return BonusAward.builder()
            .id(UUID.randomUUID()).cycleId(cycleId).employeeId(employeeId).departmentId(deptId)
            .managerEmployeeId(managerId).awardType(BonusAwardType.CYCLE)
            .currentBaseAmount(new BigDecimal("100000.00")).currency("USD").payFrequency(PayFrequency.ANNUAL)
            .targetPercent(new BigDecimal("10.00")).ratingBand(RatingBand.HIGH)
            .performanceFactor(new BigDecimal("1.2500")).companyFactor(new BigDecimal("0.8000"))
            .suggestedAmount(new BigDecimal("10000.00")).awardedAmount(new BigDecimal("10000.00"))
            .status(status).build();
    }

    private Employee managerEmployee() {
        Employee e = new Employee();
        e.setId(managerId);
        e.setUserId(managerUserId);
        return e;
    }

    @Test
    @DisplayName("activate computes the full STI award and a per-dept pool, then activates")
    void activate_generatesAwardsAndPools() {
        Employee emp = new Employee();
        emp.setId(employeeId);
        emp.setDepartmentId(deptId);
        emp.setSupervisorEmployeeId(managerId);
        emp.setStatus(EmployeeStatus.ACTIVE);

        CompensationRecord current = CompensationRecord.builder()
            .id(UUID.randomUUID()).employeeId(employeeId).baseAmount(new BigDecimal("100000.00"))
            .currency("USD").payFrequency(PayFrequency.ANNUAL).effectiveDate(LocalDate.of(2025, 1, 1))
            .isCurrent(true).build();

        BonusCycle c = cycle(ReviewCycleStatus.DRAFT);
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(c));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan()));
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(cycleDepartmentRepository.findByCycleId(cycleId)).thenReturn(List.of());
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(emp));
        when(awardRepository.existsByCycleIdAndEmployeeId(cycleId, employeeId)).thenReturn(false);
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)).thenReturn(Optional.of(current));
        when(performanceFactRepository.findFirstByCycleIdAndEmployeeIdOrderByCompletedAtDesc(any(), eq(employeeId)))
            .thenReturn(Optional.of(PerformanceFact.builder().overallRatingValue(5).potentialRatingValue(4).build()));
        when(awardRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(poolRepository.existsByCycleId(cycleId)).thenReturn(false);
        when(poolRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.activate(cycleId, actorId);

        // 100000 * 10% = 10000 ; * 1.25 (HIGH) = 12500 ; * 0.8 (company) = 10000
        ArgumentCaptor<BonusAward> a = ArgumentCaptor.forClass(BonusAward.class);
        verify(awardRepository).save(a.capture());
        assertThat(a.getValue().getRatingBand()).isEqualTo(RatingBand.HIGH);
        assertThat(a.getValue().getPerformanceFactor()).isEqualByComparingTo("1.2500");
        assertThat(a.getValue().getSuggestedAmount()).isEqualByComparingTo("10000.00");
        assertThat(a.getValue().getManagerEmployeeId()).isEqualTo(managerId);
        assertThat(a.getValue().getStatus()).isEqualTo(BonusAwardStatus.PENDING);

        ArgumentCaptor<BonusPool> pool = ArgumentCaptor.forClass(BonusPool.class);
        verify(poolRepository).save(pool.capture());
        assertThat(pool.getValue().getBasePayroll()).isEqualByComparingTo("100000.00");
        assertThat(pool.getValue().getTargetAmount()).isEqualByComparingTo("10000.00");
        assertThat(pool.getValue().getBudgetAmount()).isEqualByComparingTo("10000.00");
        assertThat(c.getStatus()).isEqualTo(ReviewCycleStatus.ACTIVE);
    }

    @Test
    @DisplayName("saveAward sets the awarded amount and marks the award PROPOSED")
    void saveAward_setsAmount() {
        BonusAward aw = award(BonusAwardStatus.PENDING);
        when(awardRepository.findById(aw.getId())).thenReturn(Optional.of(aw));
        when(employeeRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerEmployee()));
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycle(ReviewCycleStatus.ACTIVE)));
        when(poolRepository.findByCycleIdAndDepartmentId(cycleId, deptId)).thenReturn(Optional.of(
            BonusPool.builder().id(UUID.randomUUID()).cycleId(cycleId).departmentId(deptId)
                .basePayroll(new BigDecimal("100000")).targetAmount(new BigDecimal("10000"))
                .budgetAmount(new BigDecimal("20000")).build()));
        when(awardRepository.findByCycleIdAndDepartmentId(cycleId, deptId)).thenReturn(List.of(aw));
        when(awardRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BonusAwardDto result = service.saveAward(aw.getId(),
            new BonusAwardUpdateDto(new BigDecimal("8000"), "strong year"), managerUserId);

        assertThat(result.awardedAmount()).isEqualByComparingTo("8000.00");
        assertThat(result.status()).isEqualTo(BonusAwardStatus.PROPOSED);
    }

    @Test
    @DisplayName("saveAward rejects edits for someone else's report")
    void saveAward_rejectsForeignReport() {
        BonusAward aw = award(BonusAwardStatus.PENDING);
        Employee notTheManager = new Employee();
        notTheManager.setId(UUID.randomUUID());
        notTheManager.setUserId(managerUserId);
        when(awardRepository.findById(aw.getId())).thenReturn(Optional.of(aw));
        when(employeeRepository.findByUserId(managerUserId)).thenReturn(Optional.of(notTheManager));

        assertThatThrownBy(() -> service.saveAward(aw.getId(),
            new BonusAwardUpdateDto(new BigDecimal("8000"), null), managerUserId))
            .isInstanceOf(IllegalStateException.class);
        verify(awardRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveAward rejects an award that exceeds the remaining department budget")
    void saveAward_overBudgetRejected() {
        BonusAward aw = award(BonusAwardStatus.PENDING);
        when(awardRepository.findById(aw.getId())).thenReturn(Optional.of(aw));
        when(employeeRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerEmployee()));
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycle(ReviewCycleStatus.ACTIVE)));
        when(poolRepository.findByCycleIdAndDepartmentId(cycleId, deptId)).thenReturn(Optional.of(
            BonusPool.builder().id(UUID.randomUUID()).cycleId(cycleId).departmentId(deptId)
                .basePayroll(new BigDecimal("100000")).targetAmount(new BigDecimal("10000"))
                .budgetAmount(new BigDecimal("5000")).build()));
        when(awardRepository.findByCycleIdAndDepartmentId(cycleId, deptId)).thenReturn(List.of(aw));

        assertThatThrownBy(() -> service.saveAward(aw.getId(),
            new BonusAwardUpdateDto(new BigDecimal("8000"), null), managerUserId))
            .isInstanceOf(IllegalStateException.class);
        verify(awardRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve only accepts PROPOSED awards")
    void approve_onlyProposed() {
        BonusAward pending = award(BonusAwardStatus.PENDING);
        when(awardRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.approve(pending.getId(), actorId))
            .isInstanceOf(IllegalStateException.class);

        BonusAward proposed = award(BonusAwardStatus.PROPOSED);
        when(awardRepository.findById(proposed.getId())).thenReturn(Optional.of(proposed));
        when(awardRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.approve(proposed.getId(), actorId);
        assertThat(proposed.getStatus()).isEqualTo(BonusAwardStatus.APPROVED);
    }

    @Test
    @DisplayName("applyAndClose marks approved awards PAID and closes the cycle")
    void applyAndClose_marksPaid() {
        BonusAward approved = award(BonusAwardStatus.APPROVED);
        BonusCycle c = cycle(ReviewCycleStatus.IN_REVIEW);
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(c));
        when(awardRepository.findByCycleIdAndStatus(cycleId, BonusAwardStatus.APPROVED)).thenReturn(List.of(approved));
        when(awardRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.applyAndClose(cycleId, actorId);

        assertThat(approved.getStatus()).isEqualTo(BonusAwardStatus.PAID);
        assertThat(approved.getPayoutDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(c.getStatus()).isEqualTo(ReviewCycleStatus.CLOSED);
    }

    @Test
    @DisplayName("grantSpot creates a PAID spot award with no cycle")
    void grantSpot_createsPaidSpotAward() {
        Employee emp = new Employee();
        emp.setId(employeeId);
        emp.setDepartmentId(deptId);
        emp.setSupervisorEmployeeId(managerId);
        CompensationRecord current = CompensationRecord.builder()
            .id(UUID.randomUUID()).employeeId(employeeId).baseAmount(new BigDecimal("100000.00"))
            .currency("USD").payFrequency(PayFrequency.ANNUAL).isCurrent(true).build();
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(emp));
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)).thenReturn(Optional.of(current));
        when(awardRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BonusAwardDto result = service.grantSpot(
            new SpotAwardCreateDto(employeeId, null, new BigDecimal("2500"), LocalDate.of(2026, 6, 30), "spot"),
            actorId);

        assertThat(result.awardType()).isEqualTo(BonusAwardType.SPOT);
        assertThat(result.cycleId()).isNull();
        assertThat(result.awardedAmount()).isEqualByComparingTo("2500");
        assertThat(result.status()).isEqualTo(BonusAwardStatus.PAID);
    }
}
