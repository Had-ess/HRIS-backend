package com.hris.performance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail of an HR 9-box calibration move. Captures the before/after performance and
 * potential rating levels and a note, so the manager's original placement stays recoverable
 * even though the move overwrites the live values on the review. Per-review transactional data
 * (not cloned to new tenants). tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "performance_calibration_adjustments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceCalibrationAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "previous_performance_level_id")
    private UUID previousPerformanceLevelId;

    @Column(name = "new_performance_level_id")
    private UUID newPerformanceLevelId;

    @Column(name = "previous_potential_level_id")
    private UUID previousPotentialLevelId;

    @Column(name = "new_potential_level_id")
    private UUID newPotentialLevelId;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "adjusted_by")
    private UUID adjustedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
