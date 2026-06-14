package com.hris.performance.entity;

import com.hris.performance.enums.FeedbackRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One 360/peer feedback request: the review's reviewer (or HR) nominates a rater to
 * give structured feedback on the subject. The competency set the rater fills in is
 * snapshotted onto child {@link PerformanceFeedbackCompetencyRating} rows. Subject/cycle/
 * rater names are snapshotted so the rater's inbox and the attributed panel stay stable.
 * tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "performance_feedback_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceFeedbackRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "subject_employee_id", nullable = false)
    private UUID subjectEmployeeId;

    @Column(name = "subject_name", length = 200)
    private String subjectName;

    @Column(name = "cycle_name", length = 200)
    private String cycleName;

    @Column(name = "rater_employee_id", nullable = false)
    private UUID raterEmployeeId;

    @Column(name = "rater_name", length = 200)
    private String raterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FeedbackRequestStatus status = FeedbackRequestStatus.PENDING;

    @Column(columnDefinition = "text")
    private String strengths;

    @Column(columnDefinition = "text")
    private String improvements;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reminded_at")
    private Instant remindedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
