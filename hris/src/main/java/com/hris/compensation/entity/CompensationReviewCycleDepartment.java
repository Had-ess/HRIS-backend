package com.hris.compensation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Department scope row for a comp-review cycle (empty scope = all departments).
 * tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "compensation_review_cycle_departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompensationReviewCycleDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;
}
