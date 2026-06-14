package com.hris.compensation.entity;

import com.hris.compensation.enums.BonusAwardStatus;
import com.hris.compensation.enums.BonusAwardType;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.enums.RatingBand;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single bonus award — append-only variable pay, never superseding base.
 * Cycle awards (award_type CYCLE, cycle_id set) are generated on activate with a
 * snapshot of current pay + performance band and the computed STI suggestion; a
 * manager fills in the awarded amount, HR approves, apply marks PAID. SPOT awards
 * (cycle_id NULL) are ad-hoc HR grants straight to APPROVED. tenant_id is
 * unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "compensation_bonus_awards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BonusAward {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cycle_id")
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "manager_employee_id")
    private UUID managerEmployeeId;

    @Column(name = "bonus_plan_id")
    private UUID bonusPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "award_type", nullable = false, length = 20)
    @Builder.Default
    private BonusAwardType awardType = BonusAwardType.CYCLE;

    @Column(name = "current_base_amount", nullable = false)
    private BigDecimal currentBaseAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_frequency", nullable = false, length = 20)
    private PayFrequency payFrequency;

    @Column(name = "target_percent", nullable = false)
    private BigDecimal targetPercent;

    @Column(name = "performance_rating_value")
    private Integer performanceRatingValue;

    @Column(name = "potential_rating_value")
    private Integer potentialRatingValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_band", length = 10)
    private RatingBand ratingBand;

    @Column(name = "performance_factor", nullable = false)
    private BigDecimal performanceFactor;

    @Column(name = "company_factor", nullable = false)
    private BigDecimal companyFactor;

    @Column(name = "suggested_amount", nullable = false)
    private BigDecimal suggestedAmount;

    @Column(name = "awarded_amount")
    private BigDecimal awardedAmount;

    @Column(name = "payout_date")
    private LocalDate payoutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private BonusAwardStatus status = BonusAwardStatus.PENDING;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "proposed_by")
    private UUID proposedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
