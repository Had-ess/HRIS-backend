package com.hris.leave.dto;

import com.hris.leave.enums.PartialLeaveMode;
import com.hris.leave.enums.UrgencyLevel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SaveLeaveDraftDto(
    UUID leaveTypeId,
    LocalDate startDate,
    LocalDate endDate,
    UrgencyLevel urgencyLevel,
    String comment,
    Boolean isHalfDay,
    LocalTime startTime,
    LocalTime endTime,
    PartialLeaveMode partialMode
) {}
