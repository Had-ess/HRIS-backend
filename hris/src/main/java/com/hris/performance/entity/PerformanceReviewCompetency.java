package com.hris.performance.entity;

import com.hris.performance.enums.CompetencyCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A competency line on a review: the applicable competencies are snapshotted onto
 * the review at generation (name + category copied so later catalog edits never
 * change a closed review), and the manager sets {@code rating_level_id} at review
 * time. Advisory — competency ratings do NOT feed the goal-weighted computed_score.
 * tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "performance_review_competencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceReviewCompetency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "competency_id", nullable = false)
    private UUID competencyId;

    @Column(name = "competency_name", nullable = false, length = 150)
    private String competencyName;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CompetencyCategory category;

    @Column(name = "rating_level_id")
    private UUID ratingLevelId;

    @Column(columnDefinition = "text")
    private String comments;

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
