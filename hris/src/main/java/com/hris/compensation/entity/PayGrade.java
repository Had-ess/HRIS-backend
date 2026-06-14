package com.hris.compensation.entity;

import com.hris.compensation.enums.PayFrequency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-tenant pay grade / salary band (config, cloned by tenant provisioning).
 * tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "compensation_pay_grades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_frequency", nullable = false, length = 20)
    private PayFrequency payFrequency;

    @Column(name = "min_amount", nullable = false)
    private BigDecimal minAmount;

    @Column(name = "mid_amount", nullable = false)
    private BigDecimal midAmount;

    @Column(name = "max_amount", nullable = false)
    private BigDecimal maxAmount;

    @Column(name = "job_family", length = 100)
    private String jobFamily;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
