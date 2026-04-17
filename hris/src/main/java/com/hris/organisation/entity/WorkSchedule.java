package com.hris.organisation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "work_schedules")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class WorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "working_days", nullable = false, length = 255)
    private String workingDays;

    @Column(name = "hours_per_day", nullable = false)
    private int hoursPerDay;

    public Set<DayOfWeek> getWorkingDaysSet() {
        return Arrays.stream(workingDays.split(","))
            .map(String::trim)
            .map(DayOfWeek::valueOf)
            .collect(Collectors.toSet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkSchedule that = (WorkSchedule) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
