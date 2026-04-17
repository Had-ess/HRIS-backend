package com.hris.organisation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "project_departments",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_project_departments_project_dept",
        columnNames = {"project_id", "department_id"}
    ))
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ProjectDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "is_lead", nullable = false)
    @Builder.Default
    private boolean isLead = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectDepartment that = (ProjectDepartment) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
