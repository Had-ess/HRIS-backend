package com.hris.compensation.entity;

import com.hris.compensation.enums.ReviewCycleStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A merit / compensation-review cycle. Reads completed performance facts from a
 * source performance cycle and current compa-ratios, runs them through the merit
 * matrix, and drives manager proposals through a single HR approval gate.
 * tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "compensation_review_cycles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompensationReviewCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReviewCycleStatus status = ReviewCycleStatus.DRAFT;

    @Column(name = "source_performance_cycle_id")
    private UUID sourcePerformanceCycleId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "default_budget_percent", nullable = false)
    private BigDecimal defaultBudgetPercent;

    @Column(name = "rating_low_max", nullable = false)
    private int ratingLowMax;

    @Column(name = "rating_high_min", nullable = false)
    private int ratingHighMin;

    @Column(name = "compa_low_max", nullable = false)
    private BigDecimal compaLowMax;

    @Column(name = "compa_high_min", nullable = false)
    private BigDecimal compaHighMin;

    @Column(name = "include_sub_departments", nullable = false)
    @Builder.Default
    private boolean includeSubDepartments = false;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
