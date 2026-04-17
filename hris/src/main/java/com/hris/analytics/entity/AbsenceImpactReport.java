package com.hris.analytics.entity;
import com.hris.analytics.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "absence_impact_reports")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class AbsenceImpactReport {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "report_date", nullable = false) private LocalDate reportDate;
    @Column(name = "total_absence_days", nullable = false) private int totalAbsenceDays;
    @Column(name = "affected_members", nullable = false) private int affectedMembers;
    @Column(name = "estimated_delay_days", nullable = false) private int estimatedDelayDays;
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 50) private RiskLevel riskLevel;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((AbsenceImpactReport) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
