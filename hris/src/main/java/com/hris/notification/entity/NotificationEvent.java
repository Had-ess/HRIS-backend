package com.hris.notification.entity;

import com.hris.notification.enums.NotificationEventType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "notification_events")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class NotificationEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 100) private NotificationEventType eventType;
    @Column(name = "target_user_id", nullable = false) private UUID targetUserId;
    @Column(name = "title_key", nullable = false, length = 255) private String titleKey;
    @Column(name = "body_key", nullable = false, length = 255) private String bodyKey;
    @Column(nullable = false, columnDefinition = "TEXT") private String params;
    @Column(nullable = false, length = 10) private String locale;
    @Column(name = "correlation_id") private UUID correlationId;
    @Column(name = "routing_key", nullable = false, length = 100) private String routingKey;
    @Column(name = "published_at", nullable = false) @Builder.Default private Instant publishedAt = Instant.now();

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((NotificationEvent) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
