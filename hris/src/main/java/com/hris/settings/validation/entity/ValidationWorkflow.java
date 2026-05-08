package com.hris.settings.validation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "validation_workflows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ValidationUsage usage;

    @Enumerated(EnumType.STRING)
    @Column(name = "validator_source", nullable = false, length = 50)
    private ValidatorSource validatorSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_mode", nullable = false, length = 50)
    private ValidationMode validationMode;

    @Column(name = "min_validators")
    private Integer minValidators;

    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_mode", nullable = false, length = 50)
    private ValidationFallbackMode fallbackMode;

    @Column(name = "fallback_profile_id")
    private UUID fallbackProfileId;

    @Column(name = "fallback_permission_code", length = 100)
    private String fallbackPermissionCode;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultWorkflow = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationWorkflow that = (ValidationWorkflow) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
