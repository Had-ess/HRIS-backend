package com.hris.leave.dto;

import com.hris.leave.enums.PartialLeaveMode;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record LeaveRequestPreviewRequestDto(
    @NotNull UUID leaveTypeId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    LocalTime startTime,
    LocalTime endTime,
    @NotNull PartialLeaveMode partialMode
) {
}
