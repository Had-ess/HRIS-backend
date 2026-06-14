package com.hris.compensation.service;

import com.hris.analytics.entity.PerformanceFact;
import com.hris.analytics.repository.PerformanceFactRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.DepartmentRepository;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.compensation.dto.CompensationDtos.CompensationRecordDto;
import com.hris.compensation.dto.CompensationReviewDtos.ProposalDto;
import com.hris.compensation.dto.CompensationReviewDtos.ProposalUpdateDto;
import com.hris.compensation.entity.CompensationBudgetPool;
import com.hris.compensation.entity.CompensationProposal;
import com.hris.compensation.entity.CompensationRecord;
import com.hris.compensation.entity.CompensationReviewCycle;
import com.hris.compensation.enums.CompaBand;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.enums.ProposalStatus;
import com.hris.compensation.enums.RatingBand;
import com.hris.compensation.enums.ReviewCycleStatus;
import com.hris.compensation.repository.CompensationBudgetPoolRepository;
import com.hris.compensation.repository.CompensationProposalRepository;
import com.hris.compensation.repository.CompensationRecordRepository;
import com.hris.compensation.repository.CompensationReviewCycleDepartmentRepository;
import com.hris.compensation.repository.CompensationReviewCycleRepository;
import com.hris.compensation.repository.PayGradeRepository;
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
class CompensationReviewServiceTest {

    @Mock private CompensationReviewCycleRepository cycleRepository;
    @Mock private CompensationReviewCycleDepartmentRepository cycleDepartmentRepository;
    @Mock private CompensationBudgetPoolRepository poolRepository;
    @Mock private CompensationProposalRepository proposalRepository;
    @Mock private CompensationRecordRepository recordRepository;
    @Mock private PayGradeRepository payGradeRepository;
    @Mock private PerformanceFactRepository performanceFactRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private MeritMatrixService meritMatrixService;
    @Mock private CompensationService compensationService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private CompensationReviewService service;

    private final UUID cycleId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID managerUserId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private CompensationReviewCycle cycle(ReviewCycleStatus status) {
        return CompensationReviewCycle.builder()
            .id(cycleId).name("2026 Merit").status(status)
            .sourcePerformanceCycleId(UUID.randomUUID())
            .effectiveDate(LocalDate.of(2026, 7, 1))
            .defaultBudgetPercent(new BigDecimal("3.00"))
            .ratingLowMax(2).ratingHighMin(4)
            .compaLowMax(new BigDecimal("0.9000")).compaHighMin(new BigDecimal("1.1000"))
            .build();
    }

    private CompensationProposal proposal(ProposalStatus status) {
        return CompensationProposal.builder()
            .id(UUID.randomUUID()).cycleId(cycleId).employeeId(employeeId).departmentId(deptId)
            .managerEmployeeId(managerId).currentBaseAmount(new BigDecimal("100000.00"))
            .currency("USD").payFrequency(PayFrequency.ANNUAL).currentCompaRatio(new BigDecimal("0.8500"))
            .ratingBand(RatingBand.HIGH).compaBand(CompaBand.BELOW)
            .suggestedPercent(new BigDecimal("6.00")).status(status).build();
    }

    private Employee managerEmployee() {
        Employee e = new Employee();
        e.setId(managerId);
        e.setUserId(managerUserId);
        return e;
    }

    @Test
    @DisplayName("activate generates a proposal seeded from the fact + matrix and a per-dept budget pool")
    void activate_generatesProposalsAndPools() {
        Employee emp = new Employee();
        emp.setId(employeeId);
        emp.setDepartmentId(deptId);
        emp.setSupervisorEmployeeId(managerId);
        emp.setStatus(EmployeeStatus.ACTIVE);

        CompensationRecord current = CompensationRecord.builder()
            .id(UUID.randomUUID()).employeeId(employeeId).baseAmount(new BigDecimal("100000.00"))
            .currency("USD").payFrequency(PayFrequency.ANNUAL).compaRatio(new BigDecimal("0.8500"))
            .effectiveDate(LocalDate.of(2025, 1, 1)).isCurrent(true).build();

        CompensationReviewCycle c = cycle(ReviewCycleStatus.DRAFT);
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(c));
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(cycleDepartmentRepository.findByCycleId(cycleId)).thenReturn(List.of());
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(emp));
        when(proposalRepository.existsByCycleIdAndEmployeeId(cycleId, employeeId)).thenReturn(false);
        when(recordRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)).thenReturn(Optional.of(current));
        when(performanceFactRepository.findFirstByCycleIdAndEmployeeIdOrderByCompletedAtDesc(any(), eq(employeeId)))
            .thenReturn(Optional.of(PerformanceFact.builder().overallRatingValue(5).potentialRatingValue(4).build()));
        when(meritMatrixService.suggestedPercent(RatingBand.HIGH, CompaBand.BELOW))
            .thenReturn(new BigDecimal("6.00"));
        when(proposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(poolRepository.existsByCycleId(cycleId)).thenReturn(false);
        when(poolRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.activate(cycleId, actorId);

        ArgumentCaptor<CompensationProposal> p = ArgumentCaptor.forClass(CompensationProposal.class);
        verify(proposalRepository).save(p.capture());
        assertThat(p.getValue().getRatingBand()).isEqualTo(RatingBand.HIGH);
        assertThat(p.getValue().getCompaBand()).isEqualTo(CompaBand.BELOW);
        assertThat(p.getValue().getSuggestedPercent()).isEqualByComparingTo("6.00");
        assertThat(p.getValue().getManagerEmployeeId()).isEqualTo(managerId);

        ArgumentCaptor<CompensationBudgetPool> pool = ArgumentCaptor.forClass(CompensationBudgetPool.class);
        verify(poolRepository).save(pool.capture());
        assertThat(pool.getValue().getBasePayroll()).isEqualByComparingTo("100000.00");
        assertThat(pool.getValue().getBudgetAmount()).isEqualByComparingTo("3000.00");
        assertThat(c.getStatus()).isEqualTo(ReviewCycleStatus.ACTIVE);
    }

    @Test
    @DisplayName("saveProposal computes increase + new base and marks the proposal PROPOSED")
    void saveProposal_computesIncrease() {
        CompensationProposal prop = proposal(ProposalStatus.PENDING);
        when(proposalRepository.findById(prop.getId())).thenReturn(Optional.of(prop));
        when(employeeRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerEmployee()));
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycle(ReviewCycleStatus.ACTIVE)));
        when(poolRepository.findByCycleIdAndDepartmentId(cycleId, deptId)).thenReturn(Optional.of(
            CompensationBudgetPool.builder().id(UUID.randomUUID()).cycleId(cycleId).departmentId(deptId)
                .basePayroll(new BigDecimal("100000")).budgetPercent(new BigDecimal("3.00"))
                .budgetAmount(new BigDecimal("10000")).build()));
        when(proposalRepository.findByCycleIdAndDepartmentId(cycleId, deptId)).thenReturn(List.of(prop));
        when(proposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ProposalDto result = service.saveProposal(prop.getId(),
            new ProposalUpdateDto(new BigDecimal("4.0"), null, null, "merit raise"), managerUserId);

        assertThat(result.proposedIncreaseAmount()).isEqualByComparingTo("4000.00");
        assertThat(result.proposedBaseAmount()).isEqualByComparingTo("104000.00");
        assertThat(result.status()).isEqualTo(ProposalStatus.PROPOSED);
    }

    @Test
    @DisplayName("saveProposal rejects edits for someone else's report")
    void saveProposal_rejectsForeignReport() {
        CompensationProposal prop = proposal(ProposalStatus.PENDING);
        Employee notTheManager = new Employee();
        notTheManager.setId(UUID.randomUUID());
        notTheManager.setUserId(managerUserId);
        when(proposalRepository.findById(prop.getId())).thenReturn(Optional.of(prop));
        when(employeeRepository.findByUserId(managerUserId)).thenReturn(Optional.of(notTheManager));

        assertThatThrownBy(() -> service.saveProposal(prop.getId(),
            new ProposalUpdateDto(new BigDecimal("4.0"), null, null, null), managerUserId))
            .isInstanceOf(IllegalStateException.class);

        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveProposal rejects a proposal that exceeds the remaining department budget")
    void saveProposal_overBudgetRejected() {
        CompensationProposal prop = proposal(ProposalStatus.PENDING);
        when(proposalRepository.findById(prop.getId())).thenReturn(Optional.of(prop));
        when(employeeRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerEmployee()));
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycle(ReviewCycleStatus.ACTIVE)));
        when(poolRepository.findByCycleIdAndDepartmentId(cycleId, deptId)).thenReturn(Optional.of(
            CompensationBudgetPool.builder().id(UUID.randomUUID()).cycleId(cycleId).departmentId(deptId)
                .basePayroll(new BigDecimal("100000")).budgetPercent(new BigDecimal("3.00"))
                .budgetAmount(new BigDecimal("5000")).build()));
        when(proposalRepository.findByCycleIdAndDepartmentId(cycleId, deptId)).thenReturn(List.of(prop));

        // 10% of 100000 = 10000 increase > 5000 budget
        assertThatThrownBy(() -> service.saveProposal(prop.getId(),
            new ProposalUpdateDto(new BigDecimal("10.0"), null, null, null), managerUserId))
            .isInstanceOf(IllegalStateException.class);

        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve only accepts PROPOSED entries")
    void approve_onlyProposed() {
        CompensationProposal pending = proposal(ProposalStatus.PENDING);
        when(proposalRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.approve(pending.getId(), actorId))
            .isInstanceOf(IllegalStateException.class);

        CompensationProposal proposed = proposal(ProposalStatus.PROPOSED);
        when(proposalRepository.findById(proposed.getId())).thenReturn(Optional.of(proposed));
        when(proposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.approve(proposed.getId(), actorId);
        assertThat(proposed.getStatus()).isEqualTo(ProposalStatus.APPROVED);
    }

    @Test
    @DisplayName("applyAndClose writes a comp record per approved proposal and links it")
    void applyAndClose_writesRecords() {
        CompensationProposal approved = proposal(ProposalStatus.APPROVED);
        approved.setProposedBaseAmount(new BigDecimal("104000.00"));
        UUID recordId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycle(ReviewCycleStatus.IN_REVIEW)));
        when(proposalRepository.findByCycleIdAndStatus(cycleId, ProposalStatus.APPROVED))
            .thenReturn(List.of(approved));
        when(compensationService.addRecord(eq(employeeId), any(), eq(actorId))).thenReturn(
            new CompensationRecordDto(recordId, employeeId, null, null, null, new BigDecimal("104000.00"),
                "USD", PayFrequency.ANNUAL, LocalDate.of(2026, 7, 1), null, true, null,
                new BigDecimal("1.0950"), null, null));
        when(proposalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        CompensationReviewCycle c = cycle(ReviewCycleStatus.IN_REVIEW);
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(c));

        service.applyAndClose(cycleId, actorId);

        assertThat(approved.getStatus()).isEqualTo(ProposalStatus.APPLIED);
        assertThat(approved.getAppliedRecordId()).isEqualTo(recordId);
        assertThat(c.getStatus()).isEqualTo(ReviewCycleStatus.CLOSED);
        verify(compensationService).addRecord(eq(employeeId), any(), eq(actorId));
    }
}
