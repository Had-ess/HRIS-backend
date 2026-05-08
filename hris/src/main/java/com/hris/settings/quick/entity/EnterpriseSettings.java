package com.hris.settings.quick.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "enterprise_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnterpriseSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "singleton_key", nullable = false, unique = true)
    @Builder.Default
    private boolean singletonKey = true;

    @Column(name = "monthly_acquisition_rate")
    private Integer monthlyAcquisitionRate;

    @Column(name = "max_authorizations_per_month")
    private Integer maxAuthorizationsPerMonth;

    @Column(name = "max_authorization_hours")
    private Integer maxAuthorizationHours;

    @Column(name = "work_week_pattern", length = 50)
    private String workWeekPattern;

    @Column(name = "default_validation_workflow_id")
    private UUID defaultValidationWorkflowId;

    @Column(name = "default_workflow_sla_hours")
    private Integer defaultWorkflowSlaHours;

    @Column(name = "default_validation_sla_hours")
    private Integer defaultValidationSlaHours;

    @Column(name = "active_calendar_id")
    private UUID activeCalendarId;

    @Column(name = "working_hours_per_day")
    private Integer workingHoursPerDay;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnterpriseSettings that = (EnterpriseSettings) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
