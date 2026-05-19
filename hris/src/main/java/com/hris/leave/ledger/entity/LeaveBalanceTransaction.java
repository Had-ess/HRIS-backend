package com.hris.leave.ledger.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "leave_balance_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalanceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 80)
    private LeaveBalanceTransactionType type;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 10, scale = 3)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 80)
    private LeaveBalanceTransactionSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeaveBalanceTransaction that = (LeaveBalanceTransaction) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
