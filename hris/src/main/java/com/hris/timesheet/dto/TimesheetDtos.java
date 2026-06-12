package com.hris.timesheet.dto;

import com.hris.timesheet.enums.TimesheetCategory;
import com.hris.timesheet.enums.TimesheetStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response records of the timesheet API (see TIME_ATTENDANCE_DESIGN.md §6). */
public final class TimesheetDtos {

    private TimesheetDtos() {
    }

    public record CreateTimesheetRequest(@NotNull LocalDate periodStart) {
    }

    public record EntryPayload(
        @NotNull LocalDate workDate,
        UUID projectId,
        @NotNull TimesheetCategory category,
        @Positive @Max(1440) int minutes,
        @Size(max = 500) String note
    ) {
    }

    public record ReplaceEntriesRequest(@NotNull @Valid List<EntryPayload> entries) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record EntryDto(
        UUID id,
        LocalDate workDate,
        UUID projectId,
        String projectName,
        TimesheetCategory category,
        int minutes,
        String note
    ) {
    }

    public record TimesheetDto(
        UUID id,
        UUID employeeId,
        String employeeName,
        LocalDate periodStart,
        LocalDate periodEnd,
        TimesheetStatus status,
        int totalMinutes,
        Instant submittedAt,
        Instant decidedAt,
        String rejectionReason,
        List<EntryDto> entries
    ) {
    }

    /**
     * Expected-vs-declared for one sheet. Expected fields are null when the
     * employee has no work schedule (nothing sane to compare against).
     */
    public record SummaryDto(
        UUID timesheetId,
        Integer expectedWorkingDays,
        Integer leaveDays,
        Integer expectedMinutes,
        int declaredMinutes,
        Integer deltaMinutes
    ) {
    }
}
