package com.hris.performance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only progress log entry on a goal. Updating a goal's progress writes a
 * check-in. tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "performance_goal_checkins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceGoalCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "author_employee_id", nullable = false)
    private UUID authorEmployeeId;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "progress_pct", nullable = false)
    @Builder.Default
    private int progressPct = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
