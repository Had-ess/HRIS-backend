package com.hris.compensation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-department merit budget for a comp-review cycle. base_payroll is a snapshot
 * of the in-scope annualized current base; budget_amount is HR-editable and
 * defaulted from budget_percent. Allocated spend is derived from proposals, not
 * stored. tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "compensation_budget_pools")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompensationBudgetPool {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "base_payroll", nullable = false)
    private BigDecimal basePayroll;

    @Column(name = "budget_percent", nullable = false)
    private BigDecimal budgetPercent;

    @Column(name = "budget_amount", nullable = false)
    private BigDecimal budgetAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
