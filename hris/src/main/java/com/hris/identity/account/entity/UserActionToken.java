package com.hris.identity.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Single-use, time-boxed token for account activation and password reset.
 * Only the SHA-256 hash of the token is stored; the raw value exists solely
 * in the email link.
 */
@Entity
@Table(name = "user_action_tokens")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UserActionToken {

    public enum Purpose {ACTIVATION, PASSWORD_RESET}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Read-only: assigned by the database from the issuing request's tenant
     * context. The anonymous consume flows (activation/reset links carry no
     * session) read this to establish their tenant — which is why this table
     * stays RLS-exempt: the 256-bit token hash is the access control.
     */
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public boolean isUsable() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((UserActionToken) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
