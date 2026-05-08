package com.hris.analytics.entity;

import com.hris.auth.enums.EmployeeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "analytics_headcount_facts")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class HeadcountFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "team_id")
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employee_status", nullable = false, length = 50)
    private EmployeeStatus employeeStatus;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HeadcountFact that = (HeadcountFact) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
