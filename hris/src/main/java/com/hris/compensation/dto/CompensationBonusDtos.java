package com.hris.compensation.dto;

import com.hris.compensation.enums.BonusAwardStatus;
import com.hris.compensation.enums.BonusAwardType;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.enums.RatingBand;
import com.hris.compensation.enums.ReviewCycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs for compensation Phase 3 — variable / bonus pay. */
public final class CompensationBonusDtos {

    private CompensationBonusDtos() {
    }

    // --- Bonus plans ----------------------------------------------------------

    public record BonusPlanDto(
        UUID id,
        String code,
        String name,
        BigDecimal targetPercent,
        boolean isActive
    ) {
    }

    public record BonusPlanCreateDto(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @NotNull @PositiveOrZero BigDecimal targetPercent,
        Boolean isActive
    ) {
    }

    // --- Bonus cycles ---------------------------------------------------------

    public record BonusCycleDto(
        UUID id,
        String name,
        ReviewCycleStatus status,
        UUID bonusPlanId,
        String bonusPlanName,
        BigDecimal targetPercent,
        UUID sourcePerformanceCycleId,
        LocalDate payoutDate,
        BigDecimal companyFundingFactor,
        int ratingLowMax,
        int ratingHighMin,
        BigDecimal perfFactorLow,
        BigDecimal perfFactorSolid,
        BigDecimal perfFactorHigh,
        boolean includeSubDepartments,
        List<UUID> departmentIds,
        long awardCount,
        long proposedCount,
        long approvedCount,
        long paidCount,
        BigDecimal totalApprovedAmount
    ) {
    }

    public record BonusCycleCreateDto(
        @NotBlank @Size(max = 200) String name,
        @NotNull UUID bonusPlanId,
        UUID sourcePerformanceCycleId,
        @NotNull LocalDate payoutDate,
        @NotNull @Positive BigDecimal companyFundingFactor,
        @NotNull Integer ratingLowMax,
        @NotNull Integer ratingHighMin,
        @NotNull @PositiveOrZero BigDecimal perfFactorLow,
        @NotNull @PositiveOrZero BigDecimal perfFactorSolid,
        @NotNull @PositiveOrZero BigDecimal perfFactorHigh,
        Boolean includeSubDepartments,
        List<UUID> departmentIds
    ) {
    }

    // --- Bonus pools ----------------------------------------------------------

    public record BonusPoolDto(
        UUID id,
        UUID cycleId,
        UUID departmentId,
        String departmentName,
        BigDecimal basePayroll,
        BigDecimal targetAmount,
        BigDecimal budgetAmount,
        BigDecimal allocatedAmount,
        BigDecimal remainingAmount,
        long awardCount
    ) {
    }

    public record BonusPoolUpdateDto(
        @NotNull @PositiveOrZero BigDecimal budgetAmount
    ) {
    }

    // --- Bonus awards ---------------------------------------------------------

    public record BonusAwardDto(
        UUID id,
        UUID cycleId,
        UUID employeeId,
        String employeeName,
        UUID departmentId,
        String departmentName,
        UUID managerEmployeeId,
        UUID bonusPlanId,
        BonusAwardType awardType,
        BigDecimal currentBaseAmount,
        String currency,
        PayFrequency payFrequency,
        BigDecimal targetPercent,
        Integer performanceRatingValue,
        Integer potentialRatingValue,
        RatingBand ratingBand,
        BigDecimal performanceFactor,
        BigDecimal companyFactor,
        BigDecimal suggestedAmount,
        BigDecimal awardedAmount,
        LocalDate payoutDate,
        BonusAwardStatus status,
        String note
    ) {
    }

    /**
     * Manager input on a cycle award. Provide the awarded amount; null/zero clears
     * the award back to PENDING. Enforces the department bonus pool.
     */
    public record BonusAwardUpdateDto(
        @PositiveOrZero BigDecimal awardedAmount,
        @Size(max = 2000) String note
    ) {
    }

    /** HR grant of an ad-hoc spot bonus (no cycle, no pool guardrail). */
    public record SpotAwardCreateDto(
        @NotNull UUID employeeId,
        UUID bonusPlanId,
        @NotNull @Positive BigDecimal awardedAmount,
        LocalDate payoutDate,
        @Size(max = 2000) String note
    ) {
    }
}
