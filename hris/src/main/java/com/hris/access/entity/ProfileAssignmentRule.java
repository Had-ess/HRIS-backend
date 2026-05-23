package com.hris.access.entity;

import com.hris.access.enums.RuleAction;
import com.hris.access.enums.ScopeStrategy;
import com.hris.access.enums.StructuralEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "profile_assignment_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileAssignmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_event", nullable = false, length = 80)
    private StructuralEventType triggerEvent;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_strategy", nullable = false, length = 20)
    private ScopeStrategy scopeStrategy;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", insertable = false, updatable = false)
    private AccessProfile profile;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileAssignmentRule that = (ProfileAssignmentRule) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
