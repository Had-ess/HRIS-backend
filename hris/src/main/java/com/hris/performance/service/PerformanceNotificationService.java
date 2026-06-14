package com.hris.performance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hris.auth.entity.Employee;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.notification.entity.NotificationEvent;
import com.hris.notification.enums.NotificationEventType;
import com.hris.notification.service.TransactionalNotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds and publishes performance-module notifications through the existing
 * transactional outbox. Mirrors EmployeeLifecycleService.publishLifecycleNotification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceNotificationService {

    private final UserRepository userRepository;
    private final TransactionalNotificationPublisher notificationPublisher;
    private final ObjectMapper objectMapper;

    /** Notifies an employee (resolved from the employee row) that their review cycle opened. */
    public void notifyCycleOpened(Employee employee, String cycleName, String date) {
        userRepository.findById(employee.getUserId()).ifPresent(user ->
            publish(NotificationEventType.PERFORMANCE_CYCLE_OPENED, user,
                "performance.cycle.opened.title", "performance.cycle.opened.body",
                cycleParams(cycleName, date), "/performance"));
    }

    /** Reminds an employee their self-assessment is due. */
    public void notifySelfAssessmentDue(Employee employee, String cycleName, String date) {
        userRepository.findById(employee.getUserId()).ifPresent(user ->
            publish(NotificationEventType.PERFORMANCE_SELF_ASSESSMENT_DUE, user,
                "performance.selfAssessment.due.title", "performance.selfAssessment.due.body",
                cycleParams(cycleName, date), "/performance"));
    }

    /** Notifies the reviewer that an employee submitted their self-assessment. */
    public void notifyReviewSubmitted(UUID reviewerUserId, String employeeName, String cycleName) {
        userRepository.findById(reviewerUserId).ifPresent(user ->
            publish(NotificationEventType.PERFORMANCE_REVIEW_SUBMITTED, user,
                "performance.review.submitted.title", "performance.review.submitted.body",
                personParams(employeeName, cycleName), "/performance/team"));
    }

    /** Notifies the employee their manager review is ready for acknowledgement. */
    public void notifyReadyForAck(Employee employee, String employeeName, String cycleName) {
        userRepository.findById(employee.getUserId()).ifPresent(user ->
            publish(NotificationEventType.PERFORMANCE_REVIEW_READY_FOR_ACK, user,
                "performance.review.readyForAck.title", "performance.review.readyForAck.body",
                personParams(employeeName, cycleName), "/performance"));
    }

    /** Notifies a user (employee or reviewer) that a review completed. */
    public void notifyReviewCompleted(UUID userId, String employeeName, String cycleName, String linkPath) {
        userRepository.findById(userId).ifPresent(user ->
            publish(NotificationEventType.PERFORMANCE_REVIEW_COMPLETED, user,
                "performance.review.completed.title", "performance.review.completed.body",
                personParams(employeeName, cycleName), linkPath));
    }

    /** Notifies a nominated rater that peer feedback was requested from them. */
    public void notifyFeedbackRequested(UUID raterUserId, String subjectName, String cycleName) {
        userRepository.findById(raterUserId).ifPresent(user ->
            publish(NotificationEventType.PERFORMANCE_FEEDBACK_REQUESTED, user,
                "performance.feedback.requested.title", "performance.feedback.requested.body",
                personParams(subjectName, cycleName), "/performance/feedback"));
    }

    /** Notifies the reviewer that a nominated rater submitted their feedback. */
    public void notifyFeedbackSubmitted(UUID reviewerUserId, String subjectName, String cycleName) {
        userRepository.findById(reviewerUserId).ifPresent(user ->
            publish(NotificationEventType.PERFORMANCE_FEEDBACK_SUBMITTED, user,
                "performance.feedback.submitted.title", "performance.feedback.submitted.body",
                personParams(subjectName, cycleName), "/performance/team"));
    }

    private Map<String, Object> cycleParams(String cycleName, String date) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("cycleName", cycleName != null ? cycleName : "");
        params.put("date", date != null ? date : "");
        return params;
    }

    private Map<String, Object> personParams(String employeeName, String cycleName) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("employeeName", employeeName != null ? employeeName : "");
        params.put("cycleName", cycleName != null ? cycleName : "");
        return params;
    }

    private void publish(NotificationEventType eventType, User target, String titleKey, String bodyKey,
                         Map<String, Object> params, String linkPath) {
        try {
            params.put("linkPath", linkPath);
            notificationPublisher.publishAfterCommit(NotificationEvent.builder()
                .eventType(eventType)
                .targetUserId(target.getId())
                .titleKey(titleKey)
                .bodyKey(bodyKey)
                .params(objectMapper.writeValueAsString(params))
                .locale(target.getLocalePreference())
                .routingKey("performance." + eventType.name().toLowerCase())
                .publishedAt(Instant.now())
                .build());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize performance notification params for user {}", target.getId(), e);
        }
    }
}
