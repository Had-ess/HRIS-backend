package com.hris.analytics.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "headcount_metrics")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class HeadcountMetrics {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "snapshot_date", nullable = false) private LocalDate snapshotDate;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "total_employees", nullable = false) private int totalEmployees;
    @Column(name = "active_employees", nullable = false) private int activeEmployees;
    @Column(name = "new_hires_this_month", nullable = false) private int newHiresThisMonth;
    @Column(name = "departures_this_month", nullable = false) private int departuresThisMonth;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((HeadcountMetrics) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
