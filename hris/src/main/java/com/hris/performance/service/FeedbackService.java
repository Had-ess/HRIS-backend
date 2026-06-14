package com.hris.performance.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.performance.dto.PerformanceDtos.FeedbackAggregateCompetencyDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackAggregateDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackCandidateDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackCompetencyRatingDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackCompetencyRatingInput;
import com.hris.performance.dto.PerformanceDtos.FeedbackNominateDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackRequestDto;
import com.hris.performance.dto.PerformanceDtos.FeedbackSubmitDto;
import com.hris.performance.dto.PerformanceDtos.MyFeedbackRequestDto;
import com.hris.performance.dto.PerformanceDtos.RatingLevelDto;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 360 / peer feedback (PERFORMANCE_MODULE_DESIGN.md §2b). The review's reviewer (or HR
 * with PERFORMANCE_MANAGE) nominates a panel of raters; each rater rates the same
 * competency set snapshotted onto the subject's review (Phase 2a) on the cycle's scale,
 * plus free-text strengths/improvements. The subject sees only an anonymized aggregate;
 * the reviewer + HR see the attributed panel. Advisory — never feeds computed_score.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final String MANAGE_PERMISSION = "PERFORMANCE_MANAGE";

    private final PerformanceFeedbackRequestRepository feedbackRequestRepository;
    private final PerformanceFeedbackCompetencyRatingRepository feedbackRatingRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final PerformanceReviewCompetencyRepository reviewCompetencyRepository;
    private final PerformanceReviewCycleRepository cycleRepository;
    private final PerformanceRatingLevelRepository levelRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AccessScopeService accessScopeService;
    private final PerformanceNotificationService notificationService;
    private final AuditLogService auditLogService;

    // --- Nomination (reviewer / HR) ---

    /**
     * Nominates raters for a review's feedback panel. Per rater (≠ subject, deduped) creates a
     * PENDING request, snapshots the review's competency set onto it, and notifies the rater.
     * Idempotent. Returns the attributed panel.
     */
    @Transactional
    public List<FeedbackRequestDto> nominate(UUID reviewId, FeedbackNominateDto dto, UUID actorId) {
        PerformanceReview review = findReview(reviewId);
        requireNominator(review, actorId);
        Instant now = Instant.now();
        String cycleName = cycleName(review.getCycleId());
        String subjectName = employeeRepository.findById(review.getEmployeeId())
            .map(this::displayName).orElse("");
        List<PerformanceReviewCompetency> reviewCompetencies =
            reviewCompetencyRepository.findByReviewIdOrderByDisplayOrderAsc(reviewId);

        for (UUID raterId : new LinkedHashSet<>(dto.raterEmployeeIds())) {
            if (raterId.equals(review.getEmployeeId())) {
                continue; // a subject cannot rate themselves
            }
            if (feedbackRequestRepository.existsByReviewIdAndRaterEmployeeId(reviewId, raterId)) {
                continue;
            }
            Employee rater = employeeRepository.findById(raterId).orElse(null);
            if (rater == null) {
                continue;
            }
            PerformanceFeedbackRequest request = feedbackRequestRepository.save(PerformanceFeedbackRequest.builder()
                .reviewId(reviewId)
                .cycleId(review.getCycleId())
                .subjectEmployeeId(review.getEmployeeId())
                .subjectName(subjectName)
                .cycleName(cycleName)
                .raterEmployeeId(raterId)
                .raterName(displayName(rater))
                .status(FeedbackRequestStatus.PENDING)
                .requestedAt(now)
                .build());
            int order = 0;
            for (PerformanceReviewCompetency rc : reviewCompetencies) {
                feedbackRatingRepository.save(PerformanceFeedbackCompetencyRating.builder()
                    .feedbackRequestId(request.getId())
                    .competencyId(rc.getCompetencyId())
                    .competencyName(rc.getCompetencyName())
                    .category(rc.getCategory())
                    .displayOrder(order++)
                    .build());
            }
            notificationService.notifyFeedbackRequested(rater.getUserId(), subjectName, cycleName);
        }
        auditLogService.log(actorId, AuditAction.CREATE, "performance_feedback_request", reviewId, null, "NOMINATED");
        return getPanel(reviewId, actorId);
    }

    /** Removes a still-PENDING request (un-nominates a rater). Reviewer / HR only. */
    @Transactional
    public void removeRequest(UUID requestId, UUID actorId) {
        PerformanceFeedbackRequest request = findRequest(requestId);
        PerformanceReview review = findReview(request.getReviewId());
        requireNominator(review, actorId);
        if (request.getStatus() != FeedbackRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending feedback requests can be removed");
        }
        feedbackRatingRepository.deleteAll(
            feedbackRatingRepository.findByFeedbackRequestIdOrderByDisplayOrderAsc(requestId));
        feedbackRequestRepository.delete(request);
        auditLogService.log(actorId, AuditAction.DELETE, "performance_feedback_request", requestId, request, null);
    }

    /** Active employees eligible as raters: not the subject, reviewer, or already nominated. */
    @Transactional(readOnly = true)
    public List<FeedbackCandidateDto> getCandidates(UUID reviewId, UUID actorId) {
        PerformanceReview review = findReview(reviewId);
        requireNominator(review, actorId);
        Set<UUID> exclude = new LinkedHashSet<>();
        exclude.add(review.getEmployeeId());
        if (review.getReviewerEmployeeId() != null) {
            exclude.add(review.getReviewerEmployeeId());
        }
        for (PerformanceFeedbackRequest r : feedbackRequestRepository.findByReviewIdOrderByCreatedAtAsc(reviewId)) {
            exclude.add(r.getRaterEmployeeId());
        }
        return employeeRepository.findByStatus(EmployeeStatus.ACTIVE).stream()
            .filter(e -> !exclude.contains(e.getId()))
            .map(e -> new FeedbackCandidateDto(e.getId(), displayName(e)))
            .sorted(Comparator.comparing(FeedbackCandidateDto::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /** Attributed panel — reviewer / HR only. */
    @Transactional(readOnly = true)
    public List<FeedbackRequestDto> getPanel(UUID reviewId, UUID actorId) {
        PerformanceReview review = findReview(reviewId);
        requireNominator(review, actorId);
        return feedbackRequestRepository.findByReviewIdOrderByCreatedAtAsc(reviewId).stream()
            .map(this::toAttributedDto).toList();
    }

    // --- Rater inbox ---

    @Transactional(readOnly = true)
    public List<MyFeedbackRequestDto> getMyRequests(UUID userId) {
        Employee me = employee(userId);
        return feedbackRequestRepository.findByRaterEmployeeIdOrderByCreatedAtDesc(me.getId()).stream()
            .map(this::toMyDto).toList();
    }

    @Transactional(readOnly = true)
    public MyFeedbackRequestDto getMyRequest(UUID requestId, UUID userId) {
        Employee me = employee(userId);
        PerformanceFeedbackRequest request = findRequest(requestId);
        if (!me.getId().equals(request.getRaterEmployeeId())) {
            throw new EntityNotFoundException("Feedback request not found");
        }
        return toMyDto(request);
    }

    @Transactional
    public MyFeedbackRequestDto submit(UUID requestId, FeedbackSubmitDto dto, UUID userId) {
        Employee me = employee(userId);
        PerformanceFeedbackRequest request = findRequest(requestId);
        if (!me.getId().equals(request.getRaterEmployeeId())) {
            throw new IllegalArgumentException("Only the nominated rater can submit this feedback");
        }
        if (request.getStatus() != FeedbackRequestStatus.PENDING) {
            throw new IllegalStateException("This feedback request is not open");
        }
        if (dto.competencyRatings() != null && !dto.competencyRatings().isEmpty()) {
            Map<UUID, PerformanceFeedbackCompetencyRating> byId = new LinkedHashMap<>();
            for (PerformanceFeedbackCompetencyRating rc :
                    feedbackRatingRepository.findByFeedbackRequestIdOrderByDisplayOrderAsc(requestId)) {
                byId.put(rc.getId(), rc);
            }
            for (FeedbackCompetencyRatingInput input : dto.competencyRatings()) {
                PerformanceFeedbackCompetencyRating rc = byId.get(input.feedbackCompetencyRatingId());
                if (rc == null) {
                    throw new IllegalArgumentException("Competency does not belong to this feedback request");
                }
                if (input.ratingLevelId() != null) {
                    levelRepository.findById(input.ratingLevelId())
                        .orElseThrow(() -> new EntityNotFoundException("Rating level not found"));
                }
                rc.setRatingLevelId(input.ratingLevelId());
                feedbackRatingRepository.save(rc);
            }
        }
        request.setStrengths(trimToNull(dto.strengths()));
        request.setImprovements(trimToNull(dto.improvements()));
        request.setStatus(FeedbackRequestStatus.SUBMITTED);
        request.setSubmittedAt(Instant.now());
        feedbackRequestRepository.save(request);

        PerformanceReview review = reviewRepository.findById(request.getReviewId()).orElse(null);
        if (review != null && review.getReviewerEmployeeId() != null) {
            employeeRepository.findById(review.getReviewerEmployeeId()).ifPresent(reviewer ->
                notificationService.notifyFeedbackSubmitted(reviewer.getUserId(),
                    request.getSubjectName(), request.getCycleName()));
        }
        auditLogService.log(userId, AuditAction.UPDATE, "performance_feedback_request", requestId, null, "SUBMITTED");
        return toMyDto(request);
    }

    @Transactional
    public MyFeedbackRequestDto decline(UUID requestId, UUID userId) {
        Employee me = employee(userId);
        PerformanceFeedbackRequest request = findRequest(requestId);
        if (!me.getId().equals(request.getRaterEmployeeId())) {
            throw new IllegalArgumentException("Only the nominated rater can decline this feedback");
        }
        if (request.getStatus() != FeedbackRequestStatus.PENDING) {
            throw new IllegalStateException("This feedback request is not open");
        }
        request.setStatus(FeedbackRequestStatus.DECLINED);
        feedbackRequestRepository.save(request);
        auditLogService.log(userId, AuditAction.UPDATE, "performance_feedback_request", requestId, null, "DECLINED");
        return toMyDto(request);
    }

    // --- Subject anonymized aggregate ---

    /** Anonymized aggregate over SUBMITTED responses. Visible to subject, reviewer, or HR. */
    @Transactional(readOnly = true)
    public FeedbackAggregateDto getAggregate(UUID reviewId, UUID userId) {
        PerformanceReview review = findReview(reviewId);
        Employee me = employeeRepository.findByUserId(userId).orElse(null);
        boolean isSubject = me != null && me.getId().equals(review.getEmployeeId());
        boolean isReviewer = me != null && me.getId().equals(review.getReviewerEmployeeId());
        if (!isSubject && !isReviewer && !accessScopeService.hasPermissionName(userId, MANAGE_PERMISSION)) {
            throw new EntityNotFoundException("Review not found");
        }
        List<PerformanceFeedbackRequest> all = feedbackRequestRepository.findByReviewIdOrderByCreatedAtAsc(reviewId);
        List<PerformanceFeedbackRequest> submitted = all.stream()
            .filter(r -> r.getStatus() == FeedbackRequestStatus.SUBMITTED).toList();
        int pending = (int) all.stream().filter(r -> r.getStatus() == FeedbackRequestStatus.PENDING).count();

        List<String> strengths = new ArrayList<>();
        List<String> improvements = new ArrayList<>();
        for (PerformanceFeedbackRequest r : submitted) {
            if (r.getStrengths() != null && !r.getStrengths().isBlank()) {
                strengths.add(r.getStrengths().trim());
            }
            if (r.getImprovements() != null && !r.getImprovements().isBlank()) {
                improvements.add(r.getImprovements().trim());
            }
        }

        List<UUID> requestIds = submitted.stream().map(PerformanceFeedbackRequest::getId).toList();
        List<FeedbackAggregateCompetencyDto> competencies = aggregateCompetencies(requestIds);

        return new FeedbackAggregateDto(submitted.size(), pending, competencies, strengths, improvements);
    }

    private List<FeedbackAggregateCompetencyDto> aggregateCompetencies(List<UUID> submittedRequestIds) {
        if (submittedRequestIds.isEmpty()) {
            return List.of();
        }
        List<PerformanceFeedbackCompetencyRating> ratings =
            feedbackRatingRepository.findByFeedbackRequestIdInOrderByDisplayOrderAsc(submittedRequestIds);
        Map<UUID, Integer> numericByLevel = numericValuesFor(ratings);

        // Group by competency, preserving the snapshot display order.
        Map<UUID, Agg> byCompetency = new LinkedHashMap<>();
        for (PerformanceFeedbackCompetencyRating rc : ratings) {
            Agg agg = byCompetency.computeIfAbsent(rc.getCompetencyId(),
                k -> new Agg(rc.getCompetencyName(), rc.getCategory(), rc.getDisplayOrder()));
            agg.order = Math.min(agg.order, rc.getDisplayOrder());
            if (rc.getRatingLevelId() != null) {
                Integer value = numericByLevel.get(rc.getRatingLevelId());
                if (value != null) {
                    agg.sum += value;
                    agg.count++;
                }
            }
        }
        return byCompetency.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> e.getValue().order))
            .map(e -> {
                Agg a = e.getValue();
                Double avg = a.count == 0 ? null
                    : Math.round((a.sum / (double) a.count) * 100.0) / 100.0;
                return new FeedbackAggregateCompetencyDto(e.getKey(), a.name, a.category, avg, a.count);
            })
            .toList();
    }

    private Map<UUID, Integer> numericValuesFor(List<PerformanceFeedbackCompetencyRating> ratings) {
        Set<UUID> levelIds = new LinkedHashSet<>();
        for (PerformanceFeedbackCompetencyRating rc : ratings) {
            if (rc.getRatingLevelId() != null) {
                levelIds.add(rc.getRatingLevelId());
            }
        }
        Map<UUID, Integer> numeric = new LinkedHashMap<>();
        if (!levelIds.isEmpty()) {
            for (PerformanceRatingLevel level : levelRepository.findAllById(levelIds)) {
                numeric.put(level.getId(), level.getNumericValue());
            }
        }
        return numeric;
    }

    private static final class Agg {
        final String name;
        final com.hris.performance.enums.CompetencyCategory category;
        int order;
        double sum;
        int count;

        Agg(String name, com.hris.performance.enums.CompetencyCategory category, int order) {
            this.name = name;
            this.category = category;
            this.order = order;
        }
    }

    // --- Helpers ---

    private void requireNominator(PerformanceReview review, UUID actorId) {
        Employee me = employeeRepository.findByUserId(actorId).orElse(null);
        boolean isReviewer = me != null && me.getId().equals(review.getReviewerEmployeeId());
        if (!isReviewer && !accessScopeService.hasPermissionName(actorId, MANAGE_PERMISSION)) {
            throw new IllegalArgumentException("Only the reviewer or HR can manage this feedback panel");
        }
    }

    private List<RatingLevelDto> scaleLevelsForCycle(UUID cycleId) {
        PerformanceReviewCycle cycle = cycleRepository.findById(cycleId).orElse(null);
        if (cycle == null) {
            return List.of();
        }
        return levelRepository.findByScaleIdOrderByDisplayOrderAsc(cycle.getRatingScaleId()).stream()
            .map(l -> new RatingLevelDto(l.getId(), l.getLabel(), l.getNumericValue(), l.getDisplayOrder()))
            .toList();
    }

    private MyFeedbackRequestDto toMyDto(PerformanceFeedbackRequest request) {
        List<FeedbackCompetencyRatingDto> ratings =
            feedbackRatingRepository.findByFeedbackRequestIdOrderByDisplayOrderAsc(request.getId()).stream()
                .map(FeedbackService::toRatingDto).toList();
        return new MyFeedbackRequestDto(request.getId(), request.getReviewId(), request.getSubjectName(),
            request.getCycleName(), request.getStatus(), request.getStrengths(), request.getImprovements(),
            scaleLevelsForCycle(request.getCycleId()), ratings);
    }

    private FeedbackRequestDto toAttributedDto(PerformanceFeedbackRequest request) {
        List<FeedbackCompetencyRatingDto> ratings =
            feedbackRatingRepository.findByFeedbackRequestIdOrderByDisplayOrderAsc(request.getId()).stream()
                .map(FeedbackService::toRatingDto).toList();
        return new FeedbackRequestDto(request.getId(), request.getReviewId(), request.getRaterEmployeeId(),
            request.getRaterName(), request.getStatus(), request.getStrengths(), request.getImprovements(),
            request.getSubmittedAt(), ratings);
    }

    private static FeedbackCompetencyRatingDto toRatingDto(PerformanceFeedbackCompetencyRating rc) {
        return new FeedbackCompetencyRatingDto(rc.getId(), rc.getCompetencyId(), rc.getCompetencyName(),
            rc.getCategory(), rc.getRatingLevelId(), rc.getDisplayOrder());
    }

    private Employee employee(UUID userId) {
        return employeeRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    }

    private PerformanceReview findReview(UUID id) {
        return reviewRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Review not found"));
    }

    private PerformanceFeedbackRequest findRequest(UUID id) {
        return feedbackRequestRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Feedback request not found"));
    }

    private String cycleName(UUID cycleId) {
        return cycleRepository.findById(cycleId).map(PerformanceReviewCycle::getName).orElse("");
    }

    private String displayName(Employee employee) {
        return userRepository.findById(employee.getUserId())
            .map(u -> (safe(u.getFirstName()) + " " + safe(u.getLastName())).trim())
            .filter(name -> !name.isBlank())
            .orElse(employee.getEmployeeCode());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
