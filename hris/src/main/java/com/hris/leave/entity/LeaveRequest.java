package com.hris.leave.entity;

import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.enums.PartialLeaveMode;
import com.hris.leave.enums.UrgencyLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
@Where(clause = "deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class LeaveRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(name = "leave_type_id") private UUID leaveTypeId;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "working_days", nullable = false) private int workingDays;
    @Column(name = "is_half_day", nullable = false) @Builder.Default private boolean isHalfDay = false;
    @Column(name = "duration_days", nullable = false, precision = 10, scale = 3) @Builder.Default private BigDecimal durationDays = BigDecimal.ZERO;
    @Column(name = "duration_hours", nullable = false, precision = 10, scale = 3) @Builder.Default private BigDecimal durationHours = BigDecimal.ZERO;
    @Column(name = "start_time") private LocalTime startTime;
    @Column(name = "end_time") private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "partial_mode", nullable = false, length = 50)
    @Builder.Default
    private PartialLeaveMode partialMode = PartialLeaveMode.FULL_DAY;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level", length = 50)
    private UrgencyLevel urgencyLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50) private LeaveStatus status;

    @Column(columnDefinition = "TEXT") private String comment;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default private Instant submittedAt = Instant.now();

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version private Integer version;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((LeaveRequest) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
