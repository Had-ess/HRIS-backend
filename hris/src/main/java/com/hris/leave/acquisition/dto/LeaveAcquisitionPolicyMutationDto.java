package com.hris.leave.acquisition.dto;

import com.hris.leave.acquisition.entity.AcquisitionFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record LeaveAcquisitionPolicyMutationDto(
    @NotBlank @Size(max = 80) String code,
    @NotBlank @Size(max = 255) String name,
    UUID leaveTypeId,
    @NotNull AcquisitionFrequency frequency,
    @Min(0) Integer monthlyRate,
    @Min(0) Integer annualQuota,
    @Min(0) Integer dayCap,
    @Min(1) @Max(31) Integer acquisitionDay,
    Boolean prorataHire,
    Boolean negativeBalanceAllowed,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    @NotNull Boolean active
) {
}
