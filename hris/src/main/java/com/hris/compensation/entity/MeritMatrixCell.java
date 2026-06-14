package com.hris.compensation.entity;

import com.hris.compensation.enums.CompaBand;
import com.hris.compensation.enums.RatingBand;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One cell of the per-tenant merit matrix: (ratingBand x compaBand) -> suggested
 * increase %. Exactly nine cells per tenant (unique on tenant + the two bands).
 * Per-tenant config (cloned by provisioning). tenant_id is unmapped — set by DB
 * default, enforced by RLS.
 */
@Entity
@Table(name = "compensation_merit_matrix")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeritMatrixCell {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_band", nullable = false, length = 10)
    private RatingBand ratingBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "compa_band", nullable = false, length = 10)
    private CompaBand compaBand;

    @Column(name = "suggested_percent", nullable = false)
    private BigDecimal suggestedPercent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
