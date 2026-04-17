package com.hris.auth.entity;

import com.hris.auth.enums.PermissionAction;
import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "permissions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_permissions_resource_action_scope",
        columnNames = {"resource", "action", "scope"}
    ))
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String resource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PermissionAction action;

    @Column(nullable = false, length = 50)
    private String scope;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
