package com.hris.analytics.entity;

import com.hris.analytics.enums.ScopeType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "analytics_dashboards")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class AnalyticsDashboard {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "viewer_role_code", nullable = false, length = 50) private String viewerRoleCode;
    @Enumerated(EnumType.STRING) @Column(name = "scope_type", nullable = false, length = 50) private ScopeType scopeType;
    @Column(name = "scope_entity_id") private UUID scopeEntityId;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Column(name = "generated_at", nullable = false) @Builder.Default private Instant generatedAt = Instant.now();

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((AnalyticsDashboard) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
