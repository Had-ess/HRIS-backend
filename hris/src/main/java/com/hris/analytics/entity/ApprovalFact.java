package com.hris.analytics.entity;

import com.hris.analytics.enums.ApprovalSourceType;
import com.hris.approval.enums.StepStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "analytics_approval_facts")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ApprovalFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "approval_step_id", nullable = false, unique = true)
    private UUID approvalStepId;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "subject_type", nullable = false, length = 100)
    private String subjectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private ApprovalSourceType sourceType;

    @Column(name = "approver_level", nullable = false)
    private int approverLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_status", nullable = false, length = 50)
    private StepStatus stepStatus;

    @Column(name = "decision_delay_days", nullable = false)
    private int decisionDelayDays;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApprovalFact that = (ApprovalFact) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
