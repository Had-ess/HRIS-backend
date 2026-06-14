package com.hris.compensation.entity;

import com.hris.compensation.enums.CompaBand;
import com.hris.compensation.enums.CompensationChangeReason;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.enums.ProposalStatus;
import com.hris.compensation.enums.RatingBand;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One per (cycle, employee) row of a comp-review cycle. Generated on activate with
 * a snapshot of current pay + compa-ratio + performance facts and a matrix-driven
 * suggested %; a manager fills in the proposed increase, HR approves, and apply
 * writes the linked compensation record. tenant_id is unmapped — set by DB
 * default, enforced by RLS.
 */
@Entity
@Table(name = "compensation_proposals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompensationProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "manager_employee_id")
    private UUID managerEmployeeId;

    @Column(name = "pay_grade_id")
    private UUID payGradeId;

    @Column(name = "current_base_amount", nullable = false)
    private BigDecimal currentBaseAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_frequency", nullable = false, length = 20)
    private PayFrequency payFrequency;

    @Column(name = "current_compa_ratio")
    private BigDecimal currentCompaRatio;

    @Column(name = "performance_rating_value")
    private Integer performanceRatingValue;

    @Column(name = "potential_rating_value")
    private Integer potentialRatingValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_band", nullable = false, length = 10)
    private RatingBand ratingBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "compa_band", nullable = false, length = 10)
    private CompaBand compaBand;

    @Column(name = "suggested_percent", nullable = false)
    private BigDecimal suggestedPercent;

    @Column(name = "proposed_percent")
    private BigDecimal proposedPercent;

    @Column(name = "proposed_increase_amount")
    private BigDecimal proposedIncreaseAmount;

    @Column(name = "proposed_base_amount")
    private BigDecimal proposedBaseAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_reason", nullable = false, length = 30)
    @Builder.Default
    private CompensationChangeReason changeReason = CompensationChangeReason.MERIT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private ProposalStatus status = ProposalStatus.PENDING;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "proposed_by")
    private UUID proposedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "applied_record_id")
    private UUID appliedRecordId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
