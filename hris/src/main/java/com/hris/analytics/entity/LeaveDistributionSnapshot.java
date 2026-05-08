package com.hris.analytics.entity;

import com.hris.analytics.enums.AnalyticsScopeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "analytics_leave_distribution_snapshots")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class LeaveDistributionSnapshot {

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

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeaveDistributionSnapshot that = (LeaveDistributionSnapshot) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
