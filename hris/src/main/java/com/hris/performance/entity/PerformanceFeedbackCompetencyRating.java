package com.hris.performance.entity;

import com.hris.performance.enums.CompetencyCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A competency line on a feedback request: snapshotted from the subject's review
 * competencies (Phase 2a) at nomination so every rater rates the same set, stable
 * even if the catalog is later edited. The rater sets {@code rating_level_id} on
 * submit, on the cycle's rating scale. tenant_id is unmapped — DB default + RLS.
 */
@Entity
@Table(name = "performance_feedback_competency_ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceFeedbackCompetencyRating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "feedback_request_id", nullable = false)
    private UUID feedbackRequestId;

    @Column(name = "competency_id", nullable = false)
    private UUID competencyId;

    @Column(name = "competency_name", nullable = false, length = 150)
    private String competencyName;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CompetencyCategory category;

    @Column(name = "rating_level_id")
    private UUID ratingLevelId;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
