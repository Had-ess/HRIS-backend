package com.hris.performance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Maps a non-core competency to a job family (matching {@code job_titles.family}).
 * An employee's applicable competencies = core ∪ those mapped to the family of
 * their job title. tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "performance_competency_job_families")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceCompetencyJobFamily {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "competency_id", nullable = false)
    private UUID competencyId;

    @Column(name = "job_family", nullable = false, length = 150)
    private String jobFamily;
}
