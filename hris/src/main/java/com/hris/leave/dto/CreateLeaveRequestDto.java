package com.hris.leave.dto;

import com.hris.leave.enums.UrgencyLevel;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLeaveRequestDto(
    @NotNull UUID leaveTypeId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull UrgencyLevel urgencyLevel,
    String comment
) {}
