package com.hris.analytics.entity;

import com.hris.analytics.enums.AnalyticsScopeType;
import com.hris.analytics.enums.ApprovalSourceType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "analytics_approval_bottleneck_snapshots")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ApprovalBottleneckSnapshot {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private ApprovalSourceType sourceType;

    @Column(name = "approver_level", nullable = false)
    private int approverLevel;

    @Column(name = "pending_count", nullable = false)
    private int pendingCount;

    @Column(name = "average_decision_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal averageDecisionDays;

    @Column(name = "rejection_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal rejectionRate;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApprovalBottleneckSnapshot that = (ApprovalBottleneckSnapshot) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
