package com.hris.recruitment.entity;

import com.hris.recruitment.enums.CandidateSource;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recruiter-entered talent-pool record. No user account, no auth. One record per
 * email per tenant. {@code tenant_id} unmapped (DB default + RLS).
 */
@Entity
@Table(name = "recruitment_candidates")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CandidateSource source = CandidateSource.DIRECT;

    @Column(name = "current_title", length = 255)
    private String currentTitle;

    @Column(name = "current_company", length = 255)
    private String currentCompany;

    @Column(length = 100)
    private String location;

    @Column(name = "resume_url", length = 1000)
    private String resumeUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by_id")
    private UUID createdById;

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
        Candidate that = (Candidate) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
