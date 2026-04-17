package com.hris.approval.entity;

import com.hris.approval.enums.ApprovalContext;
import com.hris.approval.enums.StepStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "approval_steps")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StepStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApprovalContext context;

    @Column(name = "routing_snapshot", nullable = false, columnDefinition = "TEXT")
    private String routingSnapshot;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    private Integer version;

    public boolean isPending() {
        return status == StepStatus.PENDING;
    }

    public void approve(String comment) {
        this.status = StepStatus.APPROVED;
        this.comment = comment;
        this.decidedAt = Instant.now();
    }

    public void reject(String comment) {
        this.status = StepStatus.REJECTED;
        this.comment = comment;
        this.decidedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApprovalStep that = (ApprovalStep) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
