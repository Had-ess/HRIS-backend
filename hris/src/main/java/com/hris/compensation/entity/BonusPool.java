package com.hris.compensation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-department bonus budget for a cycle. base_payroll is a snapshot of the
 * in-scope annualized current base; target_amount is the fully-funded sum of
 * computed awards; budget_amount is the HR-editable hard cap. Allocated spend is
 * derived from awards, not stored. tenant_id is unmapped — set by DB default,
 * enforced by RLS.
 */
@Entity
@Table(name = "compensation_bonus_pools")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BonusPool {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "base_payroll", nullable = false)
    private BigDecimal basePayroll;

    @Column(name = "target_amount", nullable = false)
    private BigDecimal targetAmount;

    @Column(name = "budget_amount", nullable = false)
    private BigDecimal budgetAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
