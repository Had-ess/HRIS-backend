package com.hris.analytics.entity;

import com.hris.analytics.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "actor_id") private UUID actorId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private AuditAction action;
    @Column(nullable = false, length = 100) private String resource;
    @Column(name = "resource_id") private UUID resourceId;
    @Column(name = "previous_state", columnDefinition = "TEXT") private String previousState;
    @Column(name = "new_state", columnDefinition = "TEXT") private String newState;
    @Column(name = "ip_address", length = 45) private String ipAddress;
    @Column(name = "actor_type", length = 20) @Builder.Default private String actorType = "USER";
    @Column(nullable = false) @Builder.Default private Instant timestamp = Instant.now();

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((AuditLog) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
