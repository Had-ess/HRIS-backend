package com.hris.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "is_read", nullable = false) @Builder.Default private boolean isRead = false;
    @Column(name = "created_at", nullable = false) @Builder.Default private Instant createdAt = Instant.now();

    public void markAsRead() { this.isRead = true; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((Notification) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
