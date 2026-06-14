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
 * A variable-pay (bonus) cycle. Mirrors the merit cycle: generates one award per
 * in-scope employee seeded from a source performance cycle, runs the full STI
 * payout (target% x base x performance factor x company funding factor) inside
 * per-department pools, and routes through a single HR approval gate.
 * tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "compensation_bonus_cycles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BonusCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReviewCycleStatus status = ReviewCycleStatus.DRAFT;

    @Column(name = "bonus_plan_id", nullable = false)
    private UUID bonusPlanId;

    @Column(name = "source_performance_cycle_id")
    private UUID sourcePerformanceCycleId;

    @Column(name = "payout_date", nullable = false)
    private LocalDate payoutDate;

    @Column(name = "company_funding_factor", nullable = false)
    private BigDecimal companyFundingFactor;

    @Column(name = "rating_low_max", nullable = false)
    private int ratingLowMax;

    @Column(name = "rating_high_min", nullable = false)
    private int ratingHighMin;

    @Column(name = "perf_factor_low", nullable = false)
    private BigDecimal perfFactorLow;

    @Column(name = "perf_factor_solid", nullable = false)
    private BigDecimal perfFactorSolid;

    @Column(name = "perf_factor_high", nullable = false)
    private BigDecimal perfFactorHigh;

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
