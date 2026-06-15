package com.hris.recruitment.entity;

import com.hris.recruitment.enums.ApplicationStage;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit trail of pipeline stage moves for an application.
 * {@code tenant_id} unmapped (DB default + RLS).
 */
@Entity
@Table(name = "recruitment_application_stage_history")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ApplicationStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 20)
    private ApplicationStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false, length = 20)
    private ApplicationStage toStage;

    @Column(length = 500)
    private String note;

    @Column(name = "changed_by_id")
    private UUID changedById;

    @Column(name = "changed_at", nullable = false)
    @Builder.Default
    private Instant changedAt = Instant.now();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationStageHistory that = (ApplicationStageHistory) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
