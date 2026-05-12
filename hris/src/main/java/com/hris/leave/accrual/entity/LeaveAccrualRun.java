package com.hris.leave.accrual.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Records the execution of an accrual run for operational visibility and audit.
 */
@Entity
@Table(name = "leave_accrual_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveAccrualRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AccrualRunStatus status = AccrualRunStatus.RUNNING;

    @Column(name = "policies_processed", nullable = false)
    @Builder.Default
    private int policiesProcessed = 0;

    @Column(name = "employees_processed", nullable = false)
    @Builder.Default
    private int employeesProcessed = 0;

    @Column(name = "transactions_created", nullable = false)
    @Builder.Default
    private int transactionsCreated = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "triggered_by", nullable = false, length = 20)
    @Builder.Default
    private String triggeredBy = "SYSTEM";

    @Column(name = "triggered_by_user_id")
    private UUID triggeredByUserId;

    public void markCompleted(int policiesProcessed, int employeesProcessed, int transactionsCreated) {
        this.status = AccrualRunStatus.COMPLETED;
        this.finishedAt = Instant.now();
        this.policiesProcessed = policiesProcessed;
        this.employeesProcessed = employeesProcessed;
        this.transactionsCreated = transactionsCreated;
    }

    public void markFailed(String errorMessage) {
        this.status = AccrualRunStatus.FAILED;
        this.finishedAt = Instant.now();
        this.errorMessage = errorMessage;
    }
}
