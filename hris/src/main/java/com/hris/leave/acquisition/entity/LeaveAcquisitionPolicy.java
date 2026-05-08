package com.hris.leave.acquisition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "leave_acquisition_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveAcquisitionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AcquisitionFrequency frequency;

    @Column(name = "monthly_rate")
    private Integer monthlyRate;

    @Column(name = "annual_quota")
    private Integer annualQuota;

    @Column(name = "day_cap")
    private Integer dayCap;

    @Column(name = "acquisition_day")
    private Integer acquisitionDay;

    @Column(name = "prorata_hire", nullable = false)
    @Builder.Default
    private boolean prorataHire = false;

    @Column(name = "negative_balance_allowed", nullable = false)
    @Builder.Default
    private boolean negativeBalanceAllowed = false;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isEffectiveOn(LocalDate date) {
        return active
            && (startDate == null || !date.isBefore(startDate))
            && (endDate == null || !date.isAfter(endDate));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeaveAcquisitionPolicy that = (LeaveAcquisitionPolicy) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
