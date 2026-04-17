package com.hris.analytics.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "export_records")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class ExportRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "exported_by_id", nullable = false) private UUID exportedById;
    @Column(name = "report_type", nullable = false, length = 100) private String reportType;
    @Column(nullable = false, length = 20) private String format;
    @Column(nullable = false, length = 10) private String locale;
    @Column(name = "file_path", nullable = false, length = 500) private String filePath;
    @Column(name = "exported_at", nullable = false) @Builder.Default private Instant exportedAt = Instant.now();

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((ExportRecord) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
