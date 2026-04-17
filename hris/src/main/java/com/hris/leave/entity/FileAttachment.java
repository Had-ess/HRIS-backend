package com.hris.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "file_attachments")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class FileAttachment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "mime_type", nullable = false, length = 100) private String mimeType;
    @Column(name = "storage_path", nullable = false, length = 500) private String storagePath;
    @Column(name = "uploaded_at", nullable = false) @Builder.Default private Instant uploadedAt = Instant.now();
    @Column(name = "uploaded_by_id", nullable = false) private UUID uploadedById;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((FileAttachment) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
