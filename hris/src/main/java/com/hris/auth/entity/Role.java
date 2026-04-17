package com.hris.auth.entity;

import com.hris.auth.repository.RoleRepository;
import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "is_system_role", nullable = false)
    @Builder.Default
    private boolean isSystemRole = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "parent_id")
    private UUID parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private Role parent;

    public Integer getHierarchyLevel(RoleRepository repository) {
        return repository.computeLevel(this.id);
    }

    public boolean isHigherThan(Role other, RoleRepository repository) {
        Integer thisLevel = this.getHierarchyLevel(repository);
        Integer otherLevel = other.getHierarchyLevel(repository);
        if (thisLevel == null || otherLevel == null) return false;
        return thisLevel < otherLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return id != null && Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
