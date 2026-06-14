package com.hris.performance.entity;

import com.hris.performance.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One review per employee per cycle. The reviewer is resolved from the supervisor
 * spine at generation and denormalized here so a later supervisor change does not
 * reshuffle an in-flight cycle. It is a co-authored document with its own status
 * machine (self -> manager -> acknowledge -> complete), not an approval chain.
 * tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "performance_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "reviewer_employee_id")
    private UUID reviewerEmployeeId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "job_title", length = 255)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.NOT_STARTED;

    @Column(name = "self_comments", columnDefinition = "text")
    private String selfComments;

    @Column(name = "manager_comments", columnDefinition = "text")
    private String managerComments;

    @Column(name = "overall_rating_level_id")
    private UUID overallRatingLevelId;

    /** Confidential manager-set potential rating (the 9-box vertical axis); calibration may overwrite. */
    @Column(name = "potential_rating_level_id")
    private UUID potentialRatingLevelId;

    @Column(name = "computed_score")
    private BigDecimal computedScore;

    @Column(name = "hr_override_rating_level_id")
    private UUID hrOverrideRatingLevelId;

    @Column(name = "hr_override_by")
    private UUID hrOverrideBy;

    @Column(name = "hr_override_at")
    private Instant hrOverrideAt;

    @Column(name = "self_submitted_at")
    private Instant selfSubmittedAt;

    @Column(name = "manager_submitted_at")
    private Instant managerSubmittedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "self_reminded_at")
    private Instant selfRemindedAt;

    @Column(name = "manager_reminded_at")
    private Instant managerRemindedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
