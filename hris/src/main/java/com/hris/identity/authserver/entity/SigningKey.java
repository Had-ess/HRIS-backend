package com.hris.identity.authserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

/**
 * Persisted RSA key pair backing JWT signing and the JWKS endpoint.
 * RETIRED keys keep serving their public half so tokens signed before a
 * rotation stay verifiable until they expire.
 */
@Entity
@Table(name = "signing_keys")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class SigningKey {

    public enum Status {ACTIVE, RETIRED}

    @Id
    @Column(length = 36)
    private String kid;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "private_key", nullable = false, columnDefinition = "TEXT")
    private String privateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return kid != null && Objects.equals(kid, ((SigningKey) o).kid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(kid);
    }
}
