package com.hris.approval.entity;

import com.hris.approval.enums.WorkflowStatus;
import com.hris.settings.validation.entity.ValidationMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "approval_workflows")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ApprovalWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subject_type", nullable = false, length = 50)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WorkflowStatus status;

    @Column(name = "workflow_code", length = 80)
    private String workflowCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_mode", length = 50)
    private ValidationMode validationMode;

    @Column(name = "required_approvals")
    private Integer requiredApprovals;

    @Column(name = "routing_snapshot", columnDefinition = "TEXT")
    private String routingSnapshot;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private Integer version;

    public boolean isComplete() {
        return status == WorkflowStatus.APPROVED
            || status == WorkflowStatus.REJECTED
            || status == WorkflowStatus.CANCELLED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApprovalWorkflow that = (ApprovalWorkflow) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
