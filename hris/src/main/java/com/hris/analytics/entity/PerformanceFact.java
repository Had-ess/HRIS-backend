package com.hris.analytics.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fact row emitted when a performance review is completed (or its cycle closes).
 * Phase 1 ships the table + direct emission; the rating-distribution / completion
 * snapshots and dashboard widgets ride with the analytics phase. tenant_id is
 * unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "analytics_performance_facts")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PerformanceFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "job_title", length = 255)
    private String jobTitle;

    @Column(name = "overall_rating_value")
    private Integer overallRatingValue;

    @Column(name = "potential_rating_value")
    private Integer potentialRatingValue;

    @Column(name = "computed_score")
    private BigDecimal computedScore;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PerformanceFact that = (PerformanceFact) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
