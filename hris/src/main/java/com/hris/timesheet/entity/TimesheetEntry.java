package com.hris.timesheet.entity;

import com.hris.timesheet.enums.TimesheetCategory;
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

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** One declared block of time inside a timesheet, optionally tied to a project. */
@Entity
@Table(name = "timesheet_entries")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class TimesheetEntry {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "timesheet_id", nullable = false)
    private UUID timesheetId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimesheetCategory category;

    @Column(nullable = false)
    private int minutes;

    @Column(length = 500)
    private String note;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((TimesheetEntry) o).id);
    }

    @Override public int hashCode() { return Objects.hashCode(id); }
}
