package com.hris.leave.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @Column(name = "total_days", nullable = false) private int totalDays;
    @Column(name = "used_days", nullable = false) @Builder.Default private int usedDays = 0;
    @Column(name = "pending_days", nullable = false) @Builder.Default private int pendingDays = 0;
    @Column(name = "carry_over_days", nullable = false) @Builder.Default private int carryOverDays = 0;
    @Version private Integer version;

    public int getAvailableDays() {
        return totalDays + carryOverDays - usedDays - pendingDays;
    }

    public void adjustTotalDays(int amount) {
        this.totalDays += amount;
    }

    public void deductDays(int n) {
        if (n <= 0) throw new IllegalArgumentException("Deduction must be positive");
        this.pendingDays += n;
    }

    // GAP-B-35: Guard against going below zero
    public void restoreDays(int n) {
        if (n <= 0) throw new IllegalArgumentException("Restoration must be positive");
        this.pendingDays = Math.max(0, this.pendingDays - n);
    }

    public void confirmUsage(int n) {
        if (n <= 0) throw new IllegalArgumentException("Confirmation must be positive");
        this.pendingDays = Math.max(0, this.pendingDays - n);
        this.usedDays += n;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((LeaveBalance) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
