package com.hris.organisation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "project_teams")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ProjectTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "supervisor_employee_id", nullable = false)
    private UUID supervisorEmployeeId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectTeam that = (ProjectTeam) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
