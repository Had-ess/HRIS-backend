package com.hris.analytics.entity;

import com.hris.analytics.enums.AnalyticsScopeType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "analytics_leave_metrics_snapshots")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class LeaveMetricsSnapshot {

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

    @Column(name = "total_requests", nullable = false)
    private int totalRequests;

    @Column(name = "approved_count", nullable = false)
    private int approvedCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "pending_count", nullable = false)
    private int pendingCount;

    @Column(name = "average_processing_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal averageProcessingDays;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeaveMetricsSnapshot that = (LeaveMetricsSnapshot) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
