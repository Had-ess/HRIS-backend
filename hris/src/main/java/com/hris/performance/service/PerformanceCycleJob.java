package com.hris.performance.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.event.SystemActor;
import com.hris.performance.entity.PerformanceFeedbackRequest;
import com.hris.performance.entity.PerformanceReview;
import com.hris.performance.entity.PerformanceReviewCycle;
import com.hris.performance.enums.CycleStatus;
import com.hris.performance.enums.FeedbackRequestStatus;
import com.hris.performance.enums.ReviewStatus;
import com.hris.performance.repository.PerformanceFeedbackRequestRepository;
import com.hris.performance.repository.PerformanceReviewCycleRepository;
import com.hris.performance.repository.PerformanceReviewRepository;
import com.hris.tenancy.TenantJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily performance sweep (PERFORMANCE_MODULE_DESIGN.md §6), mirroring
 * EmployeeLifecycleJob: cycles are time-driven and re-validated each run.
 * 1. Open DRAFT cycles whose opens_on has arrived (activate + generate + notify).
 * 2. Advance ACTIVE cycles past self_assessment_due to IN_REVIEW (lock self-assessment).
 * 3. Remind employees with an open self-assessment due soon, deduplicated.
 * 4. Close cycles past closes_on, emitting completion facts.
 *
 * Gated by {@code app.performance.daily.enabled} (default false). All writes run as
 * {@link SystemActor#SYSTEM_ACTOR_ID}, normalized to null at users-FK boundaries by the
 * services (the rule that bit scheduled transfers). Per-cycle failures are logged and
 * left pending — nothing flips state on a missed day.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceCycleJob {

    static final int SELF_ASSESSMENT_REMIND_DAYS = 3;

    private final PerformanceReviewCycleRepository cycleRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final PerformanceFeedbackRequestRepository feedbackRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final ReviewCycleService reviewCycleService;
    private final PerformanceNotificationService notificationService;
    private final TenantJobRunner tenantJobRunner;

    @Value("${app.performance.daily.enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "${app.performance.daily.cron:0 0 6 * * *}")
    @SchedulerLock(name = "performanceCycleJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT2M")
    public void runDailySweep() {
        if (!enabled) {
            return;
        }
        tenantJobRunner.forEachActiveTenant("performanceCycleJob", tenant -> {
            int opened = openDueCycles();
            int advanced = advanceDueCycles();
            int reminders = sendSelfAssessmentReminders();
            int feedbackReminders = sendFeedbackReminders();
            int closed = closeDueCycles();
            log.info("Performance sweep for tenant {}: {} cycles opened, {} advanced, {} self-assessment "
                    + "reminders, {} feedback reminders, {} closed",
                tenant.getSlug(), opened, advanced, reminders, feedbackReminders, closed);
        });
    }

    @Transactional
    public int openDueCycles() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (PerformanceReviewCycle cycle :
                cycleRepository.findByStatusAndOpensOnLessThanEqual(CycleStatus.DRAFT, today)) {
            try {
                reviewCycleService.activate(cycle.getId(), SystemActor.SYSTEM_ACTOR_ID);
                count++;
            } catch (Exception e) {
                log.error("Failed to open performance cycle {}", cycle.getId(), e);
            }
        }
        return count;
    }

    @Transactional
    public int advanceDueCycles() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (PerformanceReviewCycle cycle :
                cycleRepository.findByStatusAndSelfAssessmentDueLessThan(CycleStatus.ACTIVE, today)) {
            try {
                reviewCycleService.advanceToInReview(cycle);
                count++;
            } catch (Exception e) {
                log.error("Failed to advance performance cycle {}", cycle.getId(), e);
            }
        }
        return count;
    }

    @Transactional
    public int closeDueCycles() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (CycleStatus status : List.of(CycleStatus.ACTIVE, CycleStatus.IN_REVIEW)) {
            for (PerformanceReviewCycle cycle :
                    cycleRepository.findByStatusAndClosesOnLessThanEqual(status, today)) {
                try {
                    reviewCycleService.close(cycle.getId(), SystemActor.SYSTEM_ACTOR_ID);
                    count++;
                } catch (Exception e) {
                    log.error("Failed to close performance cycle {}", cycle.getId(), e);
                }
            }
        }
        return count;
    }

    @Transactional
    public int sendSelfAssessmentReminders() {
        LocalDate today = LocalDate.now();
        int sent = 0;
        for (PerformanceReviewCycle cycle : cycleRepository.findByStatus(CycleStatus.ACTIVE)) {
            if (cycle.getSelfAssessmentDue() == null
                    || cycle.getSelfAssessmentDue().isAfter(today.plusDays(SELF_ASSESSMENT_REMIND_DAYS))) {
                continue;
            }
            for (PerformanceReview review : reviewRepository.findByCycleIdAndStatusIn(
                    cycle.getId(), List.of(ReviewStatus.SELF_ASSESSMENT))) {
                if (review.getSelfRemindedAt() != null) {
                    continue;
                }
                Employee employee = employeeRepository.findById(review.getEmployeeId()).orElse(null);
                if (employee == null) {
                    continue;
                }
                notificationService.notifySelfAssessmentDue(employee, cycle.getName(),
                    cycle.getSelfAssessmentDue().toString());
                review.setSelfRemindedAt(Instant.now());
                reviewRepository.save(review);
                sent++;
            }
        }
        return sent;
    }

    /** Reminds raters of still-pending 360/peer feedback on open cycles, once per request. */
    @Transactional
    public int sendFeedbackReminders() {
        int sent = 0;
        for (CycleStatus status : List.of(CycleStatus.ACTIVE, CycleStatus.IN_REVIEW)) {
            for (PerformanceReviewCycle cycle : cycleRepository.findByStatus(status)) {
                for (PerformanceFeedbackRequest request : feedbackRequestRepository.findByCycleIdAndStatus(
                        cycle.getId(), FeedbackRequestStatus.PENDING)) {
                    if (request.getRemindedAt() != null) {
                        continue;
                    }
                    Employee rater = employeeRepository.findById(request.getRaterEmployeeId()).orElse(null);
                    if (rater == null) {
                        continue;
                    }
                    notificationService.notifyFeedbackRequested(rater.getUserId(),
                        request.getSubjectName(), cycle.getName());
                    request.setRemindedAt(Instant.now());
                    feedbackRequestRepository.save(request);
                    sent++;
                }
            }
        }
        return sent;
    }
}
