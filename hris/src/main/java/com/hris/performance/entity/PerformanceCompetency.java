package com.hris.performance.entity;

import com.hris.performance.enums.CompetencyCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A competency in the per-tenant catalog. CORE competencies apply to everyone;
 * otherwise the competency is mapped to one or more job families
 * ({@link PerformanceCompetencyJobFamily}). Cloned to new tenants by tenant
 * provisioning. tenant_id is unmapped — set by DB default, enforced by RLS.
 */
@Entity
@Table(name = "performance_competencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceCompetency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CompetencyCategory category;

    @Column(name = "is_core", nullable = false)
    @Builder.Default
    private boolean isCore = false;

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
