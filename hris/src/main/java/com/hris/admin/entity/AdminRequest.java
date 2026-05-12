package com.hris.admin.entity;

import com.hris.admin.enums.AdminRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

@Entity @Table(name = "admin_requests")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class AdminRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "request_number", nullable = false, unique = true, length = 50) private String requestNumber;
    @Column(name = "requester_employee_id", nullable = false) private UUID requesterEmployeeId;
    @Column(name = "requester_user_id", nullable = false) private UUID requesterUserId;
    @Column(name = "type_id", nullable = false) private UUID typeId;
    @Column(nullable = false, length = 255) private String subject;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50) private AdminRequestStatus status;
    @Column(name = "rejection_reason", columnDefinition = "TEXT") private String rejectionReason;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "due_at") private Instant dueAt;
    @Column(name = "processed_by_user_id") private UUID processedByUserId;
    @Column(name = "sla_notified_at") private Instant slaNotifiedAt;
    @Column(name = "created_at", nullable = false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) @Builder.Default private Instant updatedAt = Instant.now();

    public static String generateRequestNumber() {
        return "AR-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now())
            + "-" + String.format("%05d", new Random().nextInt(100000));
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((AdminRequest) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
