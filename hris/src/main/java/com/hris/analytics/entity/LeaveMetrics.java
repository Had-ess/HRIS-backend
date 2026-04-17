package com.hris.analytics.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "leave_metrics")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class LeaveMetrics {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 20) private String period;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "total_requests", nullable = false) private int totalRequests;
    @Column(name = "approved_count", nullable = false) private int approvedCount;
    @Column(name = "rejected_count", nullable = false) private int rejectedCount;
    @Column(name = "avg_processing_days", nullable = false) private double avgProcessingDays;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((LeaveMetrics) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
