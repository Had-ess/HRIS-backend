package com.hris.leave.dto;

import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.enums.UrgencyLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestResponseDto(
    UUID id, UUID employeeId, UUID leaveTypeId, String leaveTypeCode, String leaveTypeName,
    LocalDate startDate, LocalDate endDate, int workingDays,
    UrgencyLevel urgencyLevel, LeaveStatus status,
    String comment, Instant submittedAt, boolean canUploadAttachment
) {}
