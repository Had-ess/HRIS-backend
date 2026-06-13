package com.hris.performance.service;

import com.hris.access.service.AccessResolutionService;
import com.hris.analytics.entity.PerformanceFact;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.repository.PerformanceFactRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.performance.dto.PerformanceDtos.GoalDto;
import com.hris.performance.dto.PerformanceDtos.HrOverrideDto;
import com.hris.performance.dto.PerformanceDtos.ManagerSubmitDto;
import com.hris.performance.dto.PerformanceDtos.RatingLevelDto;
import com.hris.performance.dto.PerformanceDtos.ReviewDto;
import com.hris.performance.dto.PerformanceDtos.ReviewGoalDto;
import com.hris.performance.dto.PerformanceDtos.SelfSubmitDto;
import com.hris.performance.entity.PerformanceRatingLevel;
import com.hris.performance.entity.PerformanceReview;
import com.hris.performance.entity.PerformanceReviewCycle;
import com.hris.performance.enums.ReviewStatus;
import com.hris.performance.repository.PerformanceRatingLevelRepository;
import com.hris.performance.repository.PerformanceReviewCycleRepository;
import com.hris.performance.repository.PerformanceReviewRepository;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Performance reviews: the self -> manager -> acknowledge -> complete document
 * status machine, per-record access scope, HR override, and analytics fact
 * emission on completion.
 */
@Service
@RequiredArgsConstructor
public class PerformanceReviewService {

    private static final String MANAGE_PERMISSION = "PERFORMANCE_MANAGE";

    private final PerformanceReviewRepository reviewRepository;
    private final PerformanceReviewCycleRepository cycleRepository;
    private final PerformanceRatingLevelRepository levelRepository;
    private final PerformanceFactRepository performanceFactRepository;
    private final PerformanceGoalService goalService;
    private final PerformanceNotificationService notificationService;
    private final AccessScopeService accessScopeService;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ReviewDto> getMyReviews(UUID userId) {
        Employee me = employee(userId);
        return reviewRepository.findByEmployeeIdOrderByCreatedAtDesc(me.getId()).stream()
            .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getTeamReviews(UUID userId) {
        Employee me = employee(userId);
        return reviewRepository.findByReviewerEmployeeIdOrderByCreatedAtDesc(me.getId()).stream()
            .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ReviewDto getReview(UUID id, UUID userId) {
        PerformanceReview review = findReview(id);
        if (!canView(review, userId)) {
            throw new EntityNotFoundException("Review not found");
        }
        return toDto(review);
    }

    @Transactional
    public ReviewDto selfSubmit(UUID id, SelfSubmitDto dto, UUID userId) {
        Employee me = employee(userId);
        PerformanceReview review = findReview(id);
        if (!me.getId().equals(review.getEmployeeId())) {
            throw new IllegalArgumentException("Only the employee can submit their self-assessment");
        }
        if (review.getStatus() != ReviewStatus.SELF_ASSESSMENT) {
            throw new IllegalStateException("Self-assessment is not open for this review");
        }
        goalService.validateWeightsForSubmit(review.getEmployeeId(), review.getCycleId());
        review.setSelfComments(dto.selfComments());
        review.setSelfSubmittedAt(Instant.now());
        review.setStatus(ReviewStatus.MANAGER_REVIEW);
        reviewRepository.save(review);

        if (review.getReviewerEmployeeId() != null) {
            employeeRepository.findById(review.getReviewerEmployeeId()).ifPresent(reviewer ->
                notificationService.notifyReviewSubmitted(reviewer.getUserId(),
                    displayName(me), cycleName(review.getCycleId())));
        }
        auditLogService.log(userId, AuditAction.UPDATE, "performance_review", id, null, "SELF_SUBMITTED");
        return toDto(review);
    }

    @Transactional
    public ReviewDto managerSubmit(UUID id, ManagerSubmitDto dto, UUID userId) {
        Employee me = employee(userId);
        PerformanceReview review = findReview(id);
        boolean isReviewer = me.getId().equals(review.getReviewerEmployeeId());
        if (!isReviewer && !accessScopeService.hasPermissionName(userId, MANAGE_PERMISSION)) {
            throw new IllegalArgumentException("Only the reviewer or HR can complete this review");
        }
        if (review.getStatus() != ReviewStatus.MANAGER_REVIEW) {
            throw new IllegalStateException("This review is not awaiting manager review");
        }
        goalService.applyGoalRatings(review.getEmployeeId(), review.getCycleId(), dto.goalRatings());
        if (dto.overallRatingLevelId() != null) {
            levelRepository.findById(dto.overallRatingLevelId())
                .orElseThrow(() -> new EntityNotFoundException("Rating level not found"));
        }
        review.setManagerComments(dto.managerComments());
        review.setOverallRatingLevelId(dto.overallRatingLevelId());
        review.setComputedScore(goalService.computeScore(review.getEmployeeId(), review.getCycleId()));
        review.setManagerSubmittedAt(Instant.now());
        review.setStatus(ReviewStatus.PENDING_ACKNOWLEDGEMENT);
        reviewRepository.save(review);

        employeeRepository.findById(review.getEmployeeId()).ifPresent(employee ->
            notificationService.notifyReadyForAck(employee, displayName(employee), cycleName(review.getCycleId())));
        auditLogService.log(userId, AuditAction.UPDATE, "performance_review", id, null, "MANAGER_SUBMITTED");
        return toDto(review);
    }

    @Transactional
    public ReviewDto acknowledge(UUID id, UUID userId) {
        Employee me = employee(userId);
        PerformanceReview review = findReview(id);
        if (!me.getId().equals(review.getEmployeeId())) {
            throw new IllegalArgumentException("Only the employee can acknowledge their review");
        }
        if (review.getStatus() != ReviewStatus.PENDING_ACKNOWLEDGEMENT) {
            throw new IllegalStateException("This review is not awaiting acknowledgement");
        }
        review.setStatus(ReviewStatus.COMPLETED);
        review.setAcknowledgedAt(Instant.now());
        reviewRepository.save(review);

        PerformanceReviewCycle cycle = cycleRepository.findById(review.getCycleId()).orElse(null);
        if (cycle != null) {
            emitFact(review, cycle);
        }
        notifyCompleted(review);
        auditLogService.log(userId, AuditAction.UPDATE, "performance_review", id, null, "ACKNOWLEDGED");
        return toDto(review);
    }

    @Transactional
    public ReviewDto hrOverride(UUID id, HrOverrideDto dto, UUID userId) {
        PerformanceReview review = findReview(id);
        levelRepository.findById(dto.ratingLevelId())
            .orElseThrow(() -> new EntityNotFoundException("Rating level not found"));
        review.setHrOverrideRatingLevelId(dto.ratingLevelId());
        review.setHrOverrideBy(userId);
        review.setHrOverrideAt(Instant.now());
        reviewRepository.save(review);
        auditLogService.log(userId, AuditAction.UPDATE, "performance_review", id, null, "HR_OVERRIDE");
        return toDto(review);
    }

    /** Writes the completion fact once per (cycle, employee). */
    @Transactional
    public void emitFact(PerformanceReview review, PerformanceReviewCycle cycle) {
        if (performanceFactRepository.existsByCycleIdAndEmployeeId(review.getCycleId(), review.getEmployeeId())) {
            return;
        }
        UUID effectiveLevelId = review.getHrOverrideRatingLevelId() != null
            ? review.getHrOverrideRatingLevelId()
            : review.getOverallRatingLevelId();
        Integer ratingValue = effectiveLevelId == null ? null
            : levelRepository.findById(effectiveLevelId).map(PerformanceRatingLevel::getNumericValue).orElse(null);
        performanceFactRepository.save(PerformanceFact.builder()
            .cycleId(review.getCycleId())
            .employeeId(review.getEmployeeId())
            .departmentId(review.getDepartmentId())
            .jobTitle(review.getJobTitle())
            .overallRatingValue(ratingValue)
            .computedScore(review.getComputedScore())
            .completedAt(Instant.now())
            .build());
    }

    private void notifyCompleted(PerformanceReview review) {
        String employeeName = employeeRepository.findById(review.getEmployeeId())
            .map(this::displayName).orElse("");
        String cycle = cycleName(review.getCycleId());
        employeeRepository.findById(review.getEmployeeId()).ifPresent(employee ->
            notificationService.notifyReviewCompleted(employee.getUserId(), employeeName, cycle, "/performance"));
        if (review.getReviewerEmployeeId() != null) {
            employeeRepository.findById(review.getReviewerEmployeeId()).ifPresent(reviewer ->
                notificationService.notifyReviewCompleted(reviewer.getUserId(), employeeName, cycle, "/performance/team"));
        }
    }

    private boolean canView(PerformanceReview review, UUID userId) {
        Employee me = employeeRepository.findByUserId(userId).orElse(null);
        if (me != null && (me.getId().equals(review.getEmployeeId())
                || me.getId().equals(review.getReviewerEmployeeId()))) {
            return true;
        }
        if (accessScopeService.hasPermissionName(userId, MANAGE_PERMISSION)) {
            if (accessScopeService.hasGlobalBusinessRead(userId)) {
                return true;
            }
            AccessResolutionService.ScopeResolution scope =
                accessScopeService.resolveDepartmentDataScope(userId);
            return scope.isDepartment() && review.getDepartmentId() != null
                && scope.departmentIds().contains(review.getDepartmentId());
        }
        return false;
    }

    private Employee employee(UUID userId) {
        return employeeRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    }

    private PerformanceReview findReview(UUID id) {
        return reviewRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Review not found"));
    }

    private String cycleName(UUID cycleId) {
        return cycleRepository.findById(cycleId).map(PerformanceReviewCycle::getName).orElse("");
    }

    private String displayName(Employee employee) {
        return userRepository.findById(employee.getUserId())
            .map(u -> ((safe(u.getFirstName()) + " " + safe(u.getLastName())).trim()))
            .filter(name -> !name.isBlank())
            .orElse(employee.getEmployeeCode());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private ReviewDto toDto(PerformanceReview review) {
        PerformanceReviewCycle cycle = cycleRepository.findById(review.getCycleId()).orElse(null);
        List<RatingLevelDto> levels = cycle == null ? List.of()
            : levelRepository.findByScaleIdOrderByDisplayOrderAsc(cycle.getRatingScaleId()).stream()
                .map(l -> new RatingLevelDto(l.getId(), l.getLabel(), l.getNumericValue(), l.getDisplayOrder()))
                .toList();
        List<ReviewGoalDto> goals = goalService.getGoalsForEmployeeCycle(review.getEmployeeId(), review.getCycleId())
            .stream().map(PerformanceReviewService::toReviewGoal).toList();
        Employee employee = employeeRepository.findById(review.getEmployeeId()).orElse(null);
        Employee reviewer = review.getReviewerEmployeeId() == null ? null
            : employeeRepository.findById(review.getReviewerEmployeeId()).orElse(null);
        return new ReviewDto(
            review.getId(), review.getCycleId(), cycle == null ? "" : cycle.getName(),
            review.getEmployeeId(), employee == null ? "" : displayName(employee),
            review.getReviewerEmployeeId(), reviewer == null ? null : displayName(reviewer),
            review.getDepartmentId(), review.getJobTitle(), review.getStatus(),
            review.getSelfComments(), review.getManagerComments(), review.getOverallRatingLevelId(),
            review.getComputedScore(), review.getHrOverrideRatingLevelId(),
            review.getSelfSubmittedAt(), review.getManagerSubmittedAt(), review.getAcknowledgedAt(),
            levels, goals);
    }

    private static ReviewGoalDto toReviewGoal(GoalDto g) {
        return new ReviewGoalDto(g.id(), g.title(), g.description(), g.category(), g.weight(),
            g.status(), g.progressPct(), g.dueDate(), g.ratingLevelId(), g.checkins());
    }
}
