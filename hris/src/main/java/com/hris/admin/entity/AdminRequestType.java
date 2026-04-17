package com.hris.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "admin_request_types")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class AdminRequestType {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false, length = 255) private String name;
    @Column(name = "is_active", nullable = false) @Builder.Default private boolean isActive = true;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((AdminRequestType) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
