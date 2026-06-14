package com.hris.performance.service;

import com.hris.analytics.repository.PerformanceFactRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.performance.dto.PerformanceDtos.HrOverrideDto;
import com.hris.performance.dto.PerformanceDtos.ManagerSubmitDto;
import com.hris.performance.dto.PerformanceDtos.SelfSubmitDto;
import com.hris.performance.entity.PerformanceRatingLevel;
import com.hris.performance.entity.PerformanceReview;
import com.hris.performance.entity.PerformanceReviewCycle;
import com.hris.performance.enums.ReviewStatus;
import com.hris.performance.repository.PerformanceRatingLevelRepository;
import com.hris.performance.repository.PerformanceReviewCycleRepository;
import com.hris.performance.repository.PerformanceReviewRepository;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceReviewServiceTest {

    @Mock private PerformanceReviewRepository reviewRepository;
    @Mock private PerformanceReviewCycleRepository cycleRepository;
    @Mock private PerformanceRatingLevelRepository levelRepository;
    @Mock private PerformanceFactRepository performanceFactRepository;
    @Mock private PerformanceGoalService goalService;
    @Mock private CompetencyService competencyService;
    @Mock private PerformanceNotificationService notificationService;
    @Mock private AccessScopeService accessScopeService;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private PerformanceReviewService service;

    private UUID userId;
    private UUID empId;
    private UUID cycleId;
    private Employee me;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        empId = UUID.randomUUID();
        cycleId = UUID.randomUUID();
        me = Employee.builder().id(empId).userId(userId).employeeCode("E1").build();
        // toDto support stubs (lenient — not every test reaches the mapper)
        lenient().when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(
            PerformanceReviewCycle.builder().id(cycleId).name("2026 Annual").build()));
        lenient().when(goalService.getGoalsForEmployeeCycle(any(), any())).thenReturn(List.of());
        lenient().when(levelRepository.findByScaleIdOrderByDisplayOrderAsc(any())).thenReturn(List.of());
        lenient().when(employeeRepository.findById(empId)).thenReturn(Optional.of(me));
        lenient().when(userRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(competencyService.getReviewCompetencies(any())).thenReturn(List.of());
    }

    private PerformanceReview review(ReviewStatus status, UUID employeeId, UUID reviewerId) {
        return PerformanceReview.builder().id(UUID.randomUUID()).cycleId(cycleId)
            .employeeId(employeeId).reviewerEmployeeId(reviewerId).status(status).build();
    }

    @Test
    @DisplayName("self-submit moves SELF_ASSESSMENT -> MANAGER_REVIEW after the weight rule passes")
    void selfSubmit_transitionsToManagerReview() {
        PerformanceReview r = review(ReviewStatus.SELF_ASSESSMENT, empId, null);
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(me));
        when(reviewRepository.findById(r.getId())).thenReturn(Optional.of(r));

        service.selfSubmit(r.getId(), new SelfSubmitDto("done"), userId);

        verify(goalService).validateWeightsForSubmit(empId, cycleId);
        assertThat(r.getStatus()).isEqualTo(ReviewStatus.MANAGER_REVIEW);
        assertThat(r.getSelfSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("self-submit is rejected for someone who is not the review's employee")
    void selfSubmit_rejectsNonOwner() {
        PerformanceReview r = review(ReviewStatus.SELF_ASSESSMENT, UUID.randomUUID(), null);
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(me));
        when(reviewRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.selfSubmit(r.getId(), new SelfSubmitDto("x"), userId))
            .isInstanceOf(IllegalArgumentException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("self-submit is rejected when self-assessment is not open")
    void selfSubmit_rejectsWrongStatus() {
        PerformanceReview r = review(ReviewStatus.MANAGER_REVIEW, empId, null);
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(me));
        when(reviewRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.selfSubmit(r.getId(), new SelfSubmitDto("x"), userId))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("manager-submit applies ratings, computes the score, and moves to PENDING_ACKNOWLEDGEMENT")
    void managerSubmit_transitionsToPendingAck() {
        UUID reviewerId = empId; // the caller is the reviewer
        UUID subjectId = UUID.randomUUID();
        PerformanceReview r = review(ReviewStatus.MANAGER_REVIEW, subjectId, reviewerId);
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(me));
        when(reviewRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(goalService.computeScore(subjectId, cycleId)).thenReturn(new BigDecimal("3.00"));
        lenient().when(employeeRepository.findById(subjectId)).thenReturn(Optional.of(
            Employee.builder().id(subjectId).userId(UUID.randomUUID()).employeeCode("E2").build()));

        service.managerSubmit(r.getId(), new ManagerSubmitDto("good", null, null, List.of(), List.of()), userId);

        verify(goalService).applyGoalRatings(subjectId, cycleId, List.of());
        verify(competencyService).applyCompetencyRatings(r.getId(), List.of());
        assertThat(r.getStatus()).isEqualTo(ReviewStatus.PENDING_ACKNOWLEDGEMENT);
        assertThat(r.getComputedScore()).isEqualByComparingTo("3.00");
        assertThat(r.getManagerSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("manager-submit is rejected for a non-reviewer without PERFORMANCE_MANAGE")
    void managerSubmit_rejectsNonReviewer() {
        PerformanceReview r = review(ReviewStatus.MANAGER_REVIEW, UUID.randomUUID(), UUID.randomUUID());
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(me));
        when(reviewRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(accessScopeService.hasPermissionName(userId, "PERFORMANCE_MANAGE")).thenReturn(false);

        assertThatThrownBy(() -> service.managerSubmit(r.getId(), new ManagerSubmitDto("x", null, null, List.of(), List.of()), userId))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("acknowledge completes the review and emits a fact")
    void acknowledge_completesAndEmitsFact() {
        PerformanceReview r = review(ReviewStatus.PENDING_ACKNOWLEDGEMENT, empId, null);
        when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(me));
        when(reviewRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(performanceFactRepository.existsByCycleIdAndEmployeeId(cycleId, empId)).thenReturn(false);

        service.acknowledge(r.getId(), userId);

        assertThat(r.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(r.getAcknowledgedAt()).isNotNull();
        verify(performanceFactRepository).save(any());
    }

    @Test
    @DisplayName("emitFact is idempotent — no second fact for the same (cycle, employee)")
    void emitFact_idempotent() {
        PerformanceReview r = review(ReviewStatus.COMPLETED, empId, null);
        PerformanceReviewCycle cycle = PerformanceReviewCycle.builder().id(cycleId).name("c").build();
        when(performanceFactRepository.existsByCycleIdAndEmployeeId(cycleId, empId)).thenReturn(true);

        service.emitFact(r, cycle);

        verify(performanceFactRepository, never()).save(any());
    }

    @Test
    @DisplayName("HR override records the level, actor, and timestamp")
    void hrOverride_recordsFields() {
        UUID levelId = UUID.randomUUID();
        PerformanceReview r = review(ReviewStatus.COMPLETED, empId, null);
        when(reviewRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(levelRepository.findById(levelId)).thenReturn(Optional.of(
            PerformanceRatingLevel.builder().id(levelId).numericValue(5).build()));

        service.hrOverride(r.getId(), new HrOverrideDto(levelId), userId);

        assertThat(r.getHrOverrideRatingLevelId()).isEqualTo(levelId);
        assertThat(r.getHrOverrideBy()).isEqualTo(userId);
        assertThat(r.getHrOverrideAt()).isNotNull();
    }
}
