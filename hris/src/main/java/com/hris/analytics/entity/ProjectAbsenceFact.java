package com.hris.analytics.entity;

import com.hris.analytics.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "analytics_project_absence_facts")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ProjectAbsenceFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "absent_employees", nullable = false)
    private int absentEmployees;

    @Column(name = "absence_days", nullable = false)
    private int absenceDays;

    @Column(name = "affected_members", nullable = false)
    private int affectedMembers;

    @Column(name = "estimated_delay_days", nullable = false)
    private int estimatedDelayDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 50)
    private RiskLevel riskLevel;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectAbsenceFact that = (ProjectAbsenceFact) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
