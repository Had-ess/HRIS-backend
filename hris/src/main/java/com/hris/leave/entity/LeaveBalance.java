package com.hris.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "leave_balances",
    uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "leave_type_id", "year"}))
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class LeaveBalance {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(name = "leave_type_id", nullable = false) private UUID leaveTypeId;
    @Column(nullable = false) private int year;

    @Column(name = "total_days", nullable = false, precision = 10, scale = 3) @Builder.Default private BigDecimal totalDays = BigDecimal.ZERO;
    @Column(name = "used_days", nullable = false, precision = 10, scale = 3) @Builder.Default private BigDecimal usedDays = BigDecimal.ZERO;
    @Column(name = "pending_days", nullable = false, precision = 10, scale = 3) @Builder.Default private BigDecimal pendingDays = BigDecimal.ZERO;
    @Column(name = "carry_over_days", nullable = false, precision = 10, scale = 3) @Builder.Default private BigDecimal carryOverDays = BigDecimal.ZERO;

    public BigDecimal getAvailableDays() {
        return totalDays.add(carryOverDays).subtract(usedDays).subtract(pendingDays);
    }

    public void adjustTotalDays(BigDecimal amount) {
        this.totalDays = this.totalDays.add(amount);
    }

    public void deductDays(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Deduction must be positive");
        this.pendingDays = this.pendingDays.add(amount);
    }

    public void restoreDays(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Restoration must be positive");
        this.pendingDays = this.pendingDays.subtract(amount).max(BigDecimal.ZERO);
    }

    public void confirmUsage(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Confirmation must be positive");
        this.pendingDays = this.pendingDays.subtract(amount).max(BigDecimal.ZERO);
        this.usedDays = this.usedDays.add(amount);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((LeaveBalance) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
