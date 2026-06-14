package com.hris.performance.service;

import com.hris.access.service.AccessResolutionService.ScopeResolution;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.performance.dto.PerformanceDtos.CalibrationAdjustDto;
import com.hris.performance.dto.PerformanceDtos.CalibrationGridDto;
import com.hris.performance.dto.PerformanceDtos.CalibrationReviewDto;
import com.hris.performance.entity.PerformanceCalibrationAdjustment;
import com.hris.performance.entity.PerformanceRatingLevel;
import com.hris.performance.entity.PerformanceReview;
import com.hris.performance.entity.PerformanceReviewCycle;
import com.hris.performance.enums.CycleStatus;
import com.hris.performance.enums.ReviewStatus;
import com.hris.performance.repository.PerformanceCalibrationAdjustmentRepository;
import com.hris.performance.repository.PerformanceRatingLevelRepository;
import com.hris.performance.repository.PerformanceReviewCycleRepository;
import com.hris.performance.repository.PerformanceReviewRepository;
import com.hris.security.service.AccessScopeService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalibrationServiceTest {

    @Mock private PerformanceReviewRepository reviewRepository;
    @Mock private PerformanceReviewCycleRepository cycleRepository;
    @Mock private PerformanceRatingLevelRepository levelRepository;
    @Mock private PerformanceCalibrationAdjustmentRepository adjustmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccessScopeService accessScopeService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private CalibrationService service;

    private final UUID actorId = UUID.randomUUID();
    private final UUID cycleId = UUID.randomUUID();
    private final UUID scaleId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();

    // A 1-5 scale: bands -> Low {1,2}, Mid {3}, High {4,5}; representatives -> Low=2, Mid=3, High=4.
    private final PerformanceRatingLevel l1 = level("Below", 1);
    private final PerformanceRatingLevel l2 = level("Partially", 2);
    private final PerformanceRatingLevel l3 = level("Meets", 3);
    private final PerformanceRatingLevel l4 = level("Exceeds", 4);
    private final PerformanceRatingLevel l5 = level("Outstanding", 5);

    private PerformanceRatingLevel level(String label, int value) {
        return PerformanceRatingLevel.builder().id(UUID.randomUUID()).scaleId(scaleId)
            .label(label).numericValue(value).displayOrder(value).build();
    }

    private void stubScaleAndScope() {
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(PerformanceReviewCycle.builder()
            .id(cycleId).name("2026 Annual").status(CycleStatus.IN_REVIEW).ratingScaleId(scaleId).build()));
        when(levelRepository.findByScaleIdOrderByDisplayOrderAsc(scaleId))
            .thenReturn(List.of(l1, l2, l3, l4, l5));
        when(accessScopeService.hasGlobalBusinessRead(actorId)).thenReturn(true);
        when(adjustmentRepository.existsByReviewId(any())).thenReturn(false);
        when(employeeRepository.findById(any())).thenReturn(Optional.of(
            Employee.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).employeeCode("E").build()));
    }

    private PerformanceReview review(UUID id, UUID overallLevelId, UUID potentialLevelId) {
        return PerformanceReview.builder().id(id).cycleId(cycleId).employeeId(UUID.randomUUID())
            .departmentId(deptId).jobTitle("Engineer").status(ReviewStatus.COMPLETED)
            .overallRatingLevelId(overallLevelId).potentialRatingLevelId(potentialLevelId)
            .computedScore(new BigDecimal("3.00")).build();
    }

    @Test
    @DisplayName("grid places a fully-rated review by performance and potential bands")
    void grid_placesRatedReview() {
        stubScaleAndScope();
        UUID rid = UUID.randomUUID();
        when(reviewRepository.findByCycleId(cycleId)).thenReturn(List.of(review(rid, l3.getId(), l5.getId())));

        CalibrationGridDto grid = service.getGrid(cycleId, actorId);

        assertThat(grid.placed()).hasSize(1);
        assertThat(grid.unplaced()).isEmpty();
        CalibrationReviewDto dto = grid.placed().get(0);
        assertThat(dto.performanceBand()).isEqualTo(2); // value 3 -> Mid
        assertThat(dto.potentialBand()).isEqualTo(3);   // value 5 -> High
        assertThat(dto.adjusted()).isFalse();
    }

    @Test
    @DisplayName("grid leaves a review with no potential rating unplaced")
    void grid_unplacedWithoutPotential() {
        stubScaleAndScope();
        UUID rid = UUID.randomUUID();
        when(reviewRepository.findByCycleId(cycleId)).thenReturn(List.of(review(rid, l3.getId(), null)));

        CalibrationGridDto grid = service.getGrid(cycleId, actorId);

        assertThat(grid.placed()).isEmpty();
        assertThat(grid.unplaced()).hasSize(1);
        assertThat(grid.unplaced().get(0).potentialBand()).isNull();
    }

    @Test
    @DisplayName("grid hides reviews outside the actor's department scope")
    void grid_respectsScope() {
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(PerformanceReviewCycle.builder()
            .id(cycleId).name("C").status(CycleStatus.IN_REVIEW).ratingScaleId(scaleId).build()));
        when(levelRepository.findByScaleIdOrderByDisplayOrderAsc(scaleId)).thenReturn(List.of(l1, l2, l3, l4, l5));
        when(accessScopeService.hasGlobalBusinessRead(actorId)).thenReturn(false);
        when(accessScopeService.resolveDepartmentDataScope(actorId))
            .thenReturn(ScopeResolution.department(List.of(UUID.randomUUID()))); // a different dept
        when(reviewRepository.findByCycleId(cycleId)).thenReturn(List.of(review(UUID.randomUUID(), l3.getId(), l4.getId())));

        CalibrationGridDto grid = service.getGrid(cycleId, actorId);

        assertThat(grid.placed()).isEmpty();
        assertThat(grid.unplaced()).isEmpty();
    }

    @Test
    @DisplayName("adjust remaps both axes to representative levels and records a before/after audit row")
    void adjust_movesBothAxes() {
        stubScaleAndScope();
        UUID rid = UUID.randomUUID();
        PerformanceReview r = review(rid, l3.getId(), l5.getId()); // Mid / High
        when(reviewRepository.findById(rid)).thenReturn(Optional.of(r));
        when(reviewRepository.findByCycleId(cycleId)).thenReturn(List.of(r));

        // Move to High performance (band 3) / Low potential (band 1).
        service.adjust(rid, new CalibrationAdjustDto(3, 1, "Calibration meeting"), actorId);

        assertThat(r.getHrOverrideRatingLevelId()).isEqualTo(l4.getId()); // High representative
        assertThat(r.getPotentialRatingLevelId()).isEqualTo(l2.getId());  // Low representative
        assertThat(r.getComputedScore()).isEqualByComparingTo("3.00");    // score untouched

        ArgumentCaptor<PerformanceCalibrationAdjustment> captor =
            ArgumentCaptor.forClass(PerformanceCalibrationAdjustment.class);
        verify(adjustmentRepository).save(captor.capture());
        PerformanceCalibrationAdjustment adj = captor.getValue();
        assertThat(adj.getPreviousPerformanceLevelId()).isEqualTo(l3.getId());
        assertThat(adj.getNewPerformanceLevelId()).isEqualTo(l4.getId());
        assertThat(adj.getPreviousPotentialLevelId()).isEqualTo(l5.getId());
        assertThat(adj.getNewPotentialLevelId()).isEqualTo(l2.getId());
        assertThat(adj.getNote()).isEqualTo("Calibration meeting");
        assertThat(adj.getAdjustedBy()).isEqualTo(actorId);
    }

    @Test
    @DisplayName("adjust remaps only the axis whose band changed")
    void adjust_onlyChangedAxis() {
        stubScaleAndScope();
        UUID rid = UUID.randomUUID();
        PerformanceReview r = review(rid, l3.getId(), l5.getId()); // Mid / High
        when(reviewRepository.findById(rid)).thenReturn(Optional.of(r));
        when(reviewRepository.findByCycleId(cycleId)).thenReturn(List.of(r));

        // Keep performance at Mid (band 2), move potential to Low (band 1).
        service.adjust(rid, new CalibrationAdjustDto(2, 1, null), actorId);

        assertThat(r.getHrOverrideRatingLevelId()).isNull();             // performance untouched
        assertThat(r.getPotentialRatingLevelId()).isEqualTo(l2.getId()); // potential remapped

        ArgumentCaptor<PerformanceCalibrationAdjustment> captor =
            ArgumentCaptor.forClass(PerformanceCalibrationAdjustment.class);
        verify(adjustmentRepository).save(captor.capture());
        PerformanceCalibrationAdjustment adj = captor.getValue();
        assertThat(adj.getNewPerformanceLevelId()).isEqualTo(l3.getId()); // same as previous (effective)
        assertThat(adj.getPreviousPotentialLevelId()).isEqualTo(l5.getId());
        assertThat(adj.getNewPotentialLevelId()).isEqualTo(l2.getId());
    }

    @Test
    @DisplayName("adjust rejects a review outside the actor's scope")
    void adjust_rejectsOutOfScope() {
        UUID rid = UUID.randomUUID();
        PerformanceReview r = review(rid, l3.getId(), l5.getId());
        when(reviewRepository.findById(rid)).thenReturn(Optional.of(r));
        when(accessScopeService.hasGlobalBusinessRead(actorId)).thenReturn(false);
        when(accessScopeService.resolveDepartmentDataScope(actorId))
            .thenReturn(ScopeResolution.department(List.of(UUID.randomUUID())));

        assertThatThrownBy(() -> service.adjust(rid, new CalibrationAdjustDto(1, 1, null), actorId))
            .isInstanceOf(IllegalArgumentException.class);
        verify(adjustmentRepository, never()).save(any());
    }
}
