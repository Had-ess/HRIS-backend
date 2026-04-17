package com.hris.leave.entity;

import com.hris.auth.enums.ContractType;
import jakarta.persistence.*;
import lombok.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "leave_policies",
    uniqueConstraints = @UniqueConstraint(columnNames = {"leave_type_id", "contract_type", "min_seniority_years"}))
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class LeavePolicy {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "leave_type_id", nullable = false) private UUID leaveTypeId;
    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 50) private ContractType contractType;
    @Column(name = "min_seniority_years", nullable = false) private int minSeniorityYears;
    @Column(name = "max_days_per_year", nullable = false) private int maxDaysPerYear;
    @Column(name = "carry_over_days", nullable = false) private int carryOverDays;
    @Column(name = "carry_over_expiry", nullable = false) private int carryOverExpiry;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((LeavePolicy) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
