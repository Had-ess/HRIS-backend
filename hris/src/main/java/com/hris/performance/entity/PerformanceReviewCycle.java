package com.hris.performance.entity;

import com.hris.performance.enums.CycleStatus;
import com.hris.performance.enums.CycleType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A performance review cycle. Time-driven: opens_on / self_assessment_due /
 * closes_on are re-validated by the daily job (scheduled-X pattern). tenant_id is
 * unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "performance_review_cycles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceReviewCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false, length = 30)
    private CycleType cycleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CycleStatus status = CycleStatus.DRAFT;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "self_assessment_due")
    private LocalDate selfAssessmentDue;

    @Column(name = "manager_review_due")
    private LocalDate managerReviewDue;

    @Column(name = "opens_on")
    private LocalDate opensOn;

    @Column(name = "closes_on")
    private LocalDate closesOn;

    @Column(name = "include_sub_departments", nullable = false)
    @Builder.Default
    private boolean includeSubDepartments = false;

    @Column(name = "rating_scale_id", nullable = false)
    private UUID ratingScaleId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
