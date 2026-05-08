package com.hris.analytics.entity;

import com.hris.analytics.enums.AnalyticsScopeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "analytics_headcount_metrics_snapshots")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class HeadcountMetricsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 50)
    private AnalyticsScopeType scopeType;

    @Column(name = "scope_id")
    private UUID scopeId;

    @Column(name = "total_employees", nullable = false)
    private int totalEmployees;

    @Column(name = "active_employees", nullable = false)
    private int activeEmployees;

    @Column(name = "new_hires", nullable = false)
    private int newHires;

    @Column(name = "terminated_employees", nullable = false)
    private int terminatedEmployees;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HeadcountMetricsSnapshot that = (HeadcountMetricsSnapshot) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
