package com.hris.compensation.dto;

import com.hris.compensation.enums.CompensationChangeReason;
import com.hris.compensation.enums.PayFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs for the compensation module (Phase 1: pay grades + salary records). */
public final class CompensationDtos {

    private CompensationDtos() {
    }

    // --- Pay grades -----------------------------------------------------------

    public record PayGradeDto(
        UUID id,
        String code,
        String name,
        String currency,
        PayFrequency payFrequency,
        BigDecimal minAmount,
        BigDecimal midAmount,
        BigDecimal maxAmount,
        String jobFamily,
        boolean isActive
    ) {
    }

    public record PayGradeCreateDto(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 3) String currency,
        @NotNull PayFrequency payFrequency,
        @NotNull @Positive BigDecimal minAmount,
        @NotNull @Positive BigDecimal midAmount,
        @NotNull @Positive BigDecimal maxAmount,
        @Size(max = 100) String jobFamily,
        Boolean isActive
    ) {
    }

    // --- Compensation records -------------------------------------------------

    public record CompensationRecordDto(
        UUID id,
        UUID employeeId,
        UUID payGradeId,
        String payGradeCode,
        String payGradeName,
        BigDecimal baseAmount,
        String currency,
        PayFrequency payFrequency,
        LocalDate effectiveDate,
        LocalDate endDate,
        boolean isCurrent,
        CompensationChangeReason changeReason,
        BigDecimal compaRatio,
        String note,
        Instant createdAt
    ) {
    }

    public record CompensationRecordCreateDto(
        UUID payGradeId,
        @NotNull @Positive BigDecimal baseAmount,
        @NotBlank @Size(max = 3) String currency,
        @NotNull PayFrequency payFrequency,
        @NotNull LocalDate effectiveDate,
        @NotNull CompensationChangeReason changeReason,
        @Size(max = 2000) String note
    ) {
    }

    public record MyCompensationDto(
        CompensationRecordDto current,
        List<CompensationRecordDto> history
    ) {
    }
}
