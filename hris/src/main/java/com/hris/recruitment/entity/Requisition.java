package com.hris.recruitment.entity;

import com.hris.auth.enums.ContractType;
import com.hris.recruitment.enums.RequisitionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An open role to be filled. {@code tenant_id} is unmapped — set by the DB default
 * and enforced by row-level security.
 */
@Entity
@Table(name = "recruitment_requisitions")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Requisition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "job_title_id", nullable = false)
    private UUID jobTitleId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "hiring_manager_employee_id", nullable = false)
    private UUID hiringManagerEmployeeId;

    @Column(name = "pay_grade_id")
    private UUID payGradeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 50)
    private ContractType employmentType;

    @Column(length = 100)
    private String location;

    @Column(nullable = false)
    @Builder.Default
    private int headcount = 1;

    @Column(name = "filled_count", nullable = false)
    @Builder.Default
    private int filledCount = 0;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RequisitionStatus status = RequisitionStatus.DRAFT;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_by_id")
    private UUID createdById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isFull() {
        return filledCount >= headcount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Requisition that = (Requisition) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
