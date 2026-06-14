package com.hris.performance.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.performance.dto.PerformanceDtos.FeedbackAggregateDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackCandidateDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackCompetencyRatingInput;
import com.hris.performance.dto.PerformanceDtos.FeedbackNominateDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackSubmitDto;
import com.hris.performance.entity.PerformanceFeedbackCompetencyRating;
import com.hris.performance.entity.PerformanceFeedbackRequest;
import com.hris.performance.entity.PerformanceRatingLevel;
import com.hris.performance.entity.PerformanceReview;
import com.hris.performance.entity.PerformanceReviewCompetency;
import com.hris.performance.entity.PerformanceReviewCycle;
import com.hris.performance.enums.FeedbackRequestStatus;
import com.hris.performance.repository.PerformanceFeedbackCompetencyRatingRepository;
import com.hris.performance.repository.PerformanceFeedbackRequestRepository;
import com.hris.performance.repository.PerformanceRatingLevelRepository;
import com.hris.performance.repository.PerformanceReviewCompetencyRepository;
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
class FeedbackServiceTest {

    @Mock private PerformanceFeedbackRequestRepository feedbackRequestRepository;
    @Mock private PerformanceFeedbackCompetencyRatingRepository feedbackRatingRepository;
    @Mock private PerformanceReviewRepository reviewRepository;
    @Mock private PerformanceReviewCompetencyRepository reviewCompetencyRepository;
    @Mock private PerformanceReviewCycleRepository cycleRepository;
    @Mock private PerformanceRatingLevelRepository levelRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccessScopeService accessScopeService;
    @Mock private PerformanceNotificationService notificationService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private FeedbackService service;

    private Employee emp(UUID id, UUID userId, String code) {
        return Employee.builder().id(id).userId(userId).employeeCode(code).build();
    }

    private PerformanceReview review(UUID id, UUID cycleId, UUID subjectId, UUID reviewerId) {
        return PerformanceReview.builder().id(id).cycleId(cycleId)
            .employeeId(subjectId).reviewerEmployeeId(reviewerId).build();
    }

    private PerformanceFeedbackRequest request(UUID id, UUID reviewId, UUID cycleId, UUID raterId,
                                               FeedbackRequestStatus status) {
        return PerformanceFeedbackRequest.builder().id(id).reviewId(reviewId).cycleId(cycleId)
            .subjectEmployeeId(UUID.randomUUID()).subjectName("Subject").cycleName("H1 2026")
            .raterEmployeeId(raterId).raterName("Rater").status(status).build();
    }

    @Test
    @DisplayName("nominate creates a PENDING request, snapshots competencies, and notifies the rater")
    void nominate_createsSnapshotsNotifies() {
        UUID actorUserId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID reviewerEmpId = UUID.randomUUID();
        UUID raterId = UUID.randomUUID();
        UUID raterUserId = UUID.randomUUID();
        UUID competencyId = UUID.randomUUID();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review(reviewId, cycleId, subjectId, reviewerEmpId)));
        when(employeeRepository.findByUserId(actorUserId)).thenReturn(Optional.of(emp(reviewerEmpId, actorUserId, "MGR")));
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(
            PerformanceReviewCycle.builder().id(cycleId).name("H1 2026").build()));
        when(employeeRepository.findById(subjectId)).thenReturn(Optional.of(emp(subjectId, null, "EMP-SUB")));
        when(reviewCompetencyRepository.findByReviewIdOrderByDisplayOrderAsc(reviewId)).thenReturn(List.of(
            PerformanceReviewCompetency.builder().competencyId(competencyId).competencyName("Ownership").displayOrder(0).build()));
        when(feedbackRequestRepository.existsByReviewIdAndRaterEmployeeId(reviewId, raterId)).thenReturn(false);
        when(employeeRepository.findById(raterId)).thenReturn(Optional.of(emp(raterId, raterUserId, "EMP-RTR")));
        when(feedbackRequestRepository.save(any())).thenAnswer(inv -> {
            PerformanceFeedbackRequest r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(feedbackRequestRepository.findByReviewIdOrderByCreatedAtAsc(reviewId)).thenReturn(List.of());

        service.nominate(reviewId, new FeedbackNominateDto(List.of(raterId)), actorUserId);

        ArgumentCaptor<PerformanceFeedbackRequest> reqCaptor = ArgumentCaptor.forClass(PerformanceFeedbackRequest.class);
        verify(feedbackRequestRepository).save(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getStatus()).isEqualTo(FeedbackRequestStatus.PENDING);
        assertThat(reqCaptor.getValue().getRaterEmployeeId()).isEqualTo(raterId);

        ArgumentCaptor<PerformanceFeedbackCompetencyRating> ratingCaptor =
            ArgumentCaptor.forClass(PerformanceFeedbackCompetencyRating.class);
        verify(feedbackRatingRepository).save(ratingCaptor.capture());
        assertThat(ratingCaptor.getValue().getCompetencyId()).isEqualTo(competencyId);
        assertThat(ratingCaptor.getValue().getCompetencyName()).isEqualTo("Ownership");

        verify(notificationService).notifyFeedbackRequested(raterUserId, "EMP-SUB", "H1 2026");
    }

    @Test
    @DisplayName("nominate skips the subject and already-nominated raters")
    void nominate_skipsSubjectAndDuplicates() {
        UUID actorUserId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID reviewerEmpId = UUID.randomUUID();
        UUID existingRaterId = UUID.randomUUID();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review(reviewId, cycleId, subjectId, reviewerEmpId)));
        when(employeeRepository.findByUserId(actorUserId)).thenReturn(Optional.of(emp(reviewerEmpId, actorUserId, "MGR")));
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(
            PerformanceReviewCycle.builder().id(cycleId).name("H1 2026").build()));
        when(employeeRepository.findById(subjectId)).thenReturn(Optional.of(emp(subjectId, null, "EMP-SUB")));
        when(reviewCompetencyRepository.findByReviewIdOrderByDisplayOrderAsc(reviewId)).thenReturn(List.of());
        when(feedbackRequestRepository.existsByReviewIdAndRaterEmployeeId(reviewId, existingRaterId)).thenReturn(true);
        when(feedbackRequestRepository.findByReviewIdOrderByCreatedAtAsc(reviewId)).thenReturn(List.of());

        service.nominate(reviewId, new FeedbackNominateDto(List.of(subjectId, existingRaterId)), actorUserId);

        verify(feedbackRequestRepository, never()).save(any());
        verify(notificationService, never()).notifyFeedbackRequested(any(), any(), any());
    }

    @Test
    @DisplayName("nominate is rejected for a caller who is neither the reviewer nor HR")
    void nominate_guardRejectsOutsider() {
        UUID actorUserId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID reviewerEmpId = UUID.randomUUID();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(
            review(reviewId, UUID.randomUUID(), UUID.randomUUID(), reviewerEmpId)));
        when(employeeRepository.findByUserId(actorUserId)).thenReturn(Optional.of(
            emp(UUID.randomUUID(), actorUserId, "OTHER")));
        when(accessScopeService.hasPermissionName(actorUserId, "PERFORMANCE_MANAGE")).thenReturn(false);

        assertThatThrownBy(() -> service.nominate(reviewId, new FeedbackNominateDto(List.of(UUID.randomUUID())), actorUserId))
            .isInstanceOf(IllegalArgumentException.class);
        verify(feedbackRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit sets the ratings + text, flips to SUBMITTED, and notifies the reviewer")
    void submit_setsRatingsAndNotifies() {
        UUID raterUserId = UUID.randomUUID();
        UUID raterEmpId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID reviewerEmpId = UUID.randomUUID();
        UUID reviewerUserId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        UUID scaleId = UUID.randomUUID();

        PerformanceFeedbackRequest request = request(requestId, reviewId, cycleId, raterEmpId, FeedbackRequestStatus.PENDING);
        PerformanceFeedbackCompetencyRating line = PerformanceFeedbackCompetencyRating.builder()
            .id(lineId).feedbackRequestId(requestId).competencyId(UUID.randomUUID()).competencyName("Ownership").build();

        when(employeeRepository.findByUserId(raterUserId)).thenReturn(Optional.of(emp(raterEmpId, raterUserId, "RTR")));
        when(feedbackRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(feedbackRatingRepository.findByFeedbackRequestIdOrderByDisplayOrderAsc(requestId)).thenReturn(List.of(line));
        when(levelRepository.findById(levelId)).thenReturn(Optional.of(
            PerformanceRatingLevel.builder().id(levelId).numericValue(4).build()));
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(
            review(reviewId, cycleId, UUID.randomUUID(), reviewerEmpId)));
        when(employeeRepository.findById(reviewerEmpId)).thenReturn(Optional.of(emp(reviewerEmpId, reviewerUserId, "MGR")));
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(
            PerformanceReviewCycle.builder().id(cycleId).ratingScaleId(scaleId).build()));
        when(levelRepository.findByScaleIdOrderByDisplayOrderAsc(scaleId)).thenReturn(List.of());

        service.submit(requestId, new FeedbackSubmitDto("  strong  ", "improve", List.of(
            new FeedbackCompetencyRatingInput(lineId, levelId))), raterUserId);

        assertThat(line.getRatingLevelId()).isEqualTo(levelId);
        assertThat(request.getStatus()).isEqualTo(FeedbackRequestStatus.SUBMITTED);
        assertThat(request.getStrengths()).isEqualTo("strong");
        assertThat(request.getImprovements()).isEqualTo("improve");
        assertThat(request.getSubmittedAt()).isNotNull();
        verify(notificationService).notifyFeedbackSubmitted(reviewerUserId, "Subject", "H1 2026");
    }

    @Test
    @DisplayName("submit is rejected when the caller is not the nominated rater")
    void submit_ownershipGuard() {
        UUID raterUserId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        when(employeeRepository.findByUserId(raterUserId)).thenReturn(Optional.of(
            emp(UUID.randomUUID(), raterUserId, "OTHER")));
        when(feedbackRequestRepository.findById(requestId)).thenReturn(Optional.of(
            request(requestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), FeedbackRequestStatus.PENDING)));

        assertThatThrownBy(() -> service.submit(requestId, new FeedbackSubmitDto(null, null, List.of()), raterUserId))
            .isInstanceOf(IllegalArgumentException.class);
        verify(feedbackRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeRequest is rejected once the request is no longer PENDING")
    void removeRequest_onlyPending() {
        UUID actorUserId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID reviewerEmpId = UUID.randomUUID();

        when(feedbackRequestRepository.findById(requestId)).thenReturn(Optional.of(
            request(requestId, reviewId, UUID.randomUUID(), UUID.randomUUID(), FeedbackRequestStatus.SUBMITTED)));
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(
            review(reviewId, UUID.randomUUID(), UUID.randomUUID(), reviewerEmpId)));
        when(employeeRepository.findByUserId(actorUserId)).thenReturn(Optional.of(emp(reviewerEmpId, actorUserId, "MGR")));

        assertThatThrownBy(() -> service.removeRequest(requestId, actorUserId))
            .isInstanceOf(IllegalStateException.class);
        verify(feedbackRequestRepository, never()).delete(any());
    }

    @Test
    @DisplayName("getAggregate averages SUBMITTED responses, bundles comments, and exposes no rater identity")
    void getAggregate_averagesSubmittedAndAnonymizes() {
        UUID subjectUserId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        UUID competencyId = UUID.randomUUID();
        UUID levelA = UUID.randomUUID();
        UUID levelB = UUID.randomUUID();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(
            review(reviewId, UUID.randomUUID(), subjectId, UUID.randomUUID())));
        when(employeeRepository.findByUserId(subjectUserId)).thenReturn(Optional.of(emp(subjectId, subjectUserId, "SUB")));

        PerformanceFeedbackRequest req1 = request(r1, reviewId, UUID.randomUUID(), UUID.randomUUID(), FeedbackRequestStatus.SUBMITTED);
        req1.setStrengths("good");
        PerformanceFeedbackRequest req2 = request(r2, reviewId, UUID.randomUUID(), UUID.randomUUID(), FeedbackRequestStatus.SUBMITTED);
        req2.setImprovements("more");
        when(feedbackRequestRepository.findByReviewIdOrderByCreatedAtAsc(reviewId)).thenReturn(List.of(req1, req2));

        when(feedbackRatingRepository.findByFeedbackRequestIdInOrderByDisplayOrderAsc(any())).thenReturn(List.of(
            PerformanceFeedbackCompetencyRating.builder().feedbackRequestId(r1).competencyId(competencyId)
                .competencyName("Ownership").ratingLevelId(levelA).displayOrder(0).build(),
            PerformanceFeedbackCompetencyRating.builder().feedbackRequestId(r2).competencyId(competencyId)
                .competencyName("Ownership").ratingLevelId(levelB).displayOrder(0).build()));
        when(levelRepository.findAllById(any())).thenReturn(List.of(
            PerformanceRatingLevel.builder().id(levelA).numericValue(4).build(),
            PerformanceRatingLevel.builder().id(levelB).numericValue(2).build()));

        FeedbackAggregateDto aggregate = service.getAggregate(reviewId, subjectUserId);

        assertThat(aggregate.responseCount()).isEqualTo(2);
        assertThat(aggregate.pendingCount()).isZero();
        assertThat(aggregate.competencies()).hasSize(1);
        assertThat(aggregate.competencies().get(0).competencyName()).isEqualTo("Ownership");
        assertThat(aggregate.competencies().get(0).averageRating()).isEqualTo(3.0);
        assertThat(aggregate.competencies().get(0).ratedCount()).isEqualTo(2);
        assertThat(aggregate.strengths()).containsExactly("good");
        assertThat(aggregate.improvements()).containsExactly("more");
    }

    @Test
    @DisplayName("getCandidates excludes the subject, the reviewer, and already-nominated raters")
    void getCandidates_excludesKnown() {
        UUID actorUserId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID reviewerEmpId = UUID.randomUUID();
        UUID nominatedId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(
            review(reviewId, UUID.randomUUID(), subjectId, reviewerEmpId)));
        when(employeeRepository.findByUserId(actorUserId)).thenReturn(Optional.of(emp(reviewerEmpId, actorUserId, "MGR")));
        when(feedbackRequestRepository.findByReviewIdOrderByCreatedAtAsc(reviewId)).thenReturn(List.of(
            request(UUID.randomUUID(), reviewId, UUID.randomUUID(), nominatedId, FeedbackRequestStatus.PENDING)));
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(
            emp(subjectId, null, "SUB"),
            emp(reviewerEmpId, null, "MGR"),
            emp(nominatedId, null, "NOM"),
            emp(candidateId, null, "CAND")));

        List<FeedbackCandidateDto> candidates = service.getCandidates(reviewId, actorUserId);

        assertThat(candidates).extracting(FeedbackCandidateDto::employeeId).containsExactly(candidateId);
    }
}
