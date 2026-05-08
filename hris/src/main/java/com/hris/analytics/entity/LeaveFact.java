package com.hris.analytics.entity;

import com.hris.leave.enums.LeaveStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "analytics_leave_facts")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class LeaveFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "leave_request_id", nullable = false, unique = true)
    private UUID leaveRequestId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "working_days", nullable = false)
    private int workingDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 50)
    private LeaveStatus requestStatus;

    @Column(name = "approval_duration_days", nullable = false)
    private int approvalDurationDays;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeaveFact leaveFact = (LeaveFact) o;
        return id != null && Objects.equals(id, leaveFact.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
