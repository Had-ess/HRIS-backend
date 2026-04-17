package com.hris.admin.entity;

import com.hris.admin.enums.AdminRequestStatus;
import com.hris.leave.enums.UrgencyLevel;
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
    @Column(name = "requester_id", nullable = false) private UUID requesterId;
    @Column(name = "request_type_id", nullable = false) private UUID requestTypeId;
    @Column(name = "tracking_number", nullable = false, unique = true, length = 50) private String trackingNumber;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level", nullable = false, length = 50) private UrgencyLevel urgencyLevel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50) private AdminRequestStatus status;
    @Column(columnDefinition = "TEXT") private String metadata;
    @Column(name = "submitted_at", nullable = false) @Builder.Default private Instant submittedAt = Instant.now();
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolved_by_id") private UUID resolvedById;

    public static String generateTrackingNumber() {
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
