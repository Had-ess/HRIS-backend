package com.hris.leave.entity;

import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.enums.UrgencyLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class LeaveRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(name = "leave_type_id", nullable = false) private UUID leaveTypeId;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "working_days", nullable = false) private int workingDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level", nullable = false, length = 50)
    private UrgencyLevel urgencyLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50) private LeaveStatus status;

    @Column(columnDefinition = "TEXT") private String comment;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default private Instant submittedAt = Instant.now();

    @Version private Integer version;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((LeaveRequest) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
