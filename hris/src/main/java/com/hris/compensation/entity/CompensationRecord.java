package com.hris.compensation.entity;

import com.hris.compensation.enums.CompensationChangeReason;
import com.hris.compensation.enums.PayFrequency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Effective-dated per-employee salary history. One row is current per employee
 * (partial unique index uq_comp_records_current); a new record supersedes the
 * prior current row. tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "compensation_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompensationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "pay_grade_id")
    private UUID payGradeId;

    @Column(name = "base_amount", nullable = false)
    private BigDecimal baseAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_frequency", nullable = false, length = 20)
    private PayFrequency payFrequency;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private boolean isCurrent = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_reason", nullable = false, length = 30)
    private CompensationChangeReason changeReason;

    @Column(name = "compa_ratio")
    private BigDecimal compaRatio;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
