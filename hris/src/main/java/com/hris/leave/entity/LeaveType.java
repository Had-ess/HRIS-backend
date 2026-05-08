package com.hris.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "leave_types")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class LeaveType {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(nullable = false, unique = true, length = 50) private String code;

    @Column(nullable = false, length = 255) private String name;
    @Column(name = "is_paid", nullable = false) @Builder.Default private boolean isPaid = true;
    @Column(name = "requires_justification", nullable = false) @Builder.Default private boolean requiresJustification = false;
    @Column(name = "is_active", nullable = false) @Builder.Default private boolean isActive = true;
    @Column(name = "balance_tracked", nullable = false) @Builder.Default private boolean balanceTracked = true;
    @Column(name = "validation_workflow_id") private UUID validationWorkflowId;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((LeaveType) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
