package com.hris.performance.entity;

import com.hris.performance.enums.GoalCategory;
import com.hris.performance.enums.GoalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A weighted goal. Per (employee, cycle) the ACTIVE goal weights must sum to 100
 * before the self-assessment can be submitted. cycle_id is nullable so a goal can
 * outlive a single cycle. rating_level_id is the manager's per-goal rating at
 * review time, which drives the weighted score. tenant_id is unmapped — set by DB
 * default, enforced by RLS.
 */
@Entity
@Table(name = "performance_goals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "cycle_id")
    private UUID cycleId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private GoalCategory category;

    @Column(nullable = false)
    @Builder.Default
    private int weight = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GoalStatus status = GoalStatus.DRAFT;

    @Column(name = "progress_pct", nullable = false)
    @Builder.Default
    private int progressPct = 0;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "rating_level_id")
    private UUID ratingLevelId;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
