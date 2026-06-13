package com.hris.performance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

/**
 * Optional population scope for a cycle: the departments it covers. Empty set =
 * all active employees in the tenant. Composite PK (cycle_id, department_id);
 * tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "performance_review_cycle_departments")
@IdClass(PerformanceReviewCycleDepartment.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceReviewCycleDepartment {

    @Id
    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Id
    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private UUID cycleId;
        private UUID departmentId;
    }
}
