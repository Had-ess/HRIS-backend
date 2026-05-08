package com.hris.leave.acquisition.dto;

import com.hris.leave.acquisition.entity.AcquisitionFrequency;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveAcquisitionPolicyDto(
    UUID id,
    String code,
    String name,
    UUID leaveTypeId,
    String leaveTypeCode,
    String leaveTypeName,
    AcquisitionFrequency frequency,
    Integer monthlyRate,
    Integer annualQuota,
    Integer dayCap,
    Integer acquisitionDay,
    boolean prorataHire,
    boolean negativeBalanceAllowed,
    LocalDate startDate,
    LocalDate endDate,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
