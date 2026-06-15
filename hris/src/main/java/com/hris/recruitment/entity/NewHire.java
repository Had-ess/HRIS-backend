package com.hris.recruitment.entity;

import com.hris.recruitment.enums.NewHireStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The handoff bridge created when an application reaches HIRED. HR finalizes it through
 * the authoritative employee-creation flow, which links the created employee back here
 * and increments the requisition's filled count. {@code tenant_id} unmapped (DB default + RLS).
 */
@Entity
@Table(name = "recruitment_new_hires")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class NewHire {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "requisition_id", nullable = false)
    private UUID requisitionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NewHireStatus status = NewHireStatus.PENDING;

    @Column(name = "target_start_date")
    private LocalDate targetStartDate;

    @Column(name = "created_employee_id")
    private UUID createdEmployeeId;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "finalized_by_id")
    private UUID finalizedById;

    @Column(name = "created_by_id")
    private UUID createdById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NewHire that = (NewHire) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
