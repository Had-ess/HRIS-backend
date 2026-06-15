package com.hris.recruitment.entity;

import com.hris.recruitment.enums.ApplicationStage;
import com.hris.recruitment.enums.CandidateSource;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A candidate's application to a requisition — the pipeline record. A candidate applies
 * to a given requisition at most once. {@code tenant_id} unmapped (DB default + RLS).
 */
@Entity
@Table(name = "recruitment_applications")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requisition_id", nullable = false)
    private UUID requisitionId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApplicationStage stage = ApplicationStage.APPLIED;

    @Column
    private Short rating;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CandidateSource source = CandidateSource.DIRECT;

    @Column(name = "applied_at", nullable = false)
    @Builder.Default
    private Instant appliedAt = Instant.now();

    @Column(name = "stage_changed_at", nullable = false)
    @Builder.Default
    private Instant stageChangedAt = Instant.now();

    @Column(name = "hired_at")
    private Instant hiredAt;

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
        Application that = (Application) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
