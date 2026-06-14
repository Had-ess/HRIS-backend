package com.hris.compensation.dto;

import com.hris.compensation.enums.CompaBand;
import com.hris.compensation.enums.CompensationChangeReason;
import com.hris.compensation.enums.PayFrequency;
import com.hris.compensation.enums.ProposalStatus;
import com.hris.compensation.enums.RatingBand;
import com.hris.compensation.enums.ReviewCycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs for compensation Phase 2 — merit matrix + comp-review cycles. */
public final class CompensationReviewDtos {

    private CompensationReviewDtos() {
    }

    // --- Merit matrix ---------------------------------------------------------

    public record MeritMatrixCellDto(
        UUID id,
        RatingBand ratingBand,
        CompaBand compaBand,
        BigDecimal suggestedPercent
    ) {
    }

    public record MeritMatrixCellUpdateDto(
        @NotNull RatingBand ratingBand,
        @NotNull CompaBand compaBand,
        @NotNull @PositiveOrZero BigDecimal suggestedPercent
    ) {
    }

    public record MeritMatrixUpdateDto(
        @NotNull List<MeritMatrixCellUpdateDto> cells
    ) {
    }

    // --- Review cycles --------------------------------------------------------

    public record ReviewCycleDto(
        UUID id,
        String name,
        ReviewCycleStatus status,
        UUID sourcePerformanceCycleId,
        LocalDate effectiveDate,
        BigDecimal defaultBudgetPercent,
        int ratingLowMax,
        int ratingHighMin,
        BigDecimal compaLowMax,
        BigDecimal compaHighMin,
        boolean includeSubDepartments,
        List<UUID> departmentIds,
        long proposalCount,
        long proposedCount,
        long approvedCount,
        long appliedCount,
        BigDecimal totalApprovedAmount
    ) {
    }

    public record ReviewCycleCreateDto(
        @NotBlank @Size(max = 200) String name,
        UUID sourcePerformanceCycleId,
        @NotNull LocalDate effectiveDate,
        @NotNull @PositiveOrZero BigDecimal defaultBudgetPercent,
        @NotNull Integer ratingLowMax,
        @NotNull Integer ratingHighMin,
        @NotNull @PositiveOrZero BigDecimal compaLowMax,
        @NotNull @PositiveOrZero BigDecimal compaHighMin,
        Boolean includeSubDepartments,
        List<UUID> departmentIds
    ) {
    }

    // --- Budget pools ---------------------------------------------------------

    public record BudgetPoolDto(
        UUID id,
        UUID cycleId,
        UUID departmentId,
        String departmentName,
        BigDecimal basePayroll,
        BigDecimal budgetPercent,
        BigDecimal budgetAmount,
        BigDecimal allocatedAmount,
        BigDecimal remainingAmount,
        long proposalCount
    ) {
    }

    public record BudgetPoolUpdateDto(
        @NotNull @PositiveOrZero BigDecimal budgetAmount
    ) {
    }

    // --- Proposals ------------------------------------------------------------

    public record ProposalDto(
        UUID id,
        UUID cycleId,
        UUID employeeId,
        String employeeName,
        UUID departmentId,
        String departmentName,
        UUID managerEmployeeId,
        UUID payGradeId,
        String payGradeCode,
        BigDecimal currentBaseAmount,
        String currency,
        PayFrequency payFrequency,
        BigDecimal currentCompaRatio,
        Integer performanceRatingValue,
        Integer potentialRatingValue,
        RatingBand ratingBand,
        CompaBand compaBand,
        BigDecimal suggestedPercent,
        BigDecimal proposedPercent,
        BigDecimal proposedIncreaseAmount,
        BigDecimal proposedBaseAmount,
        BigDecimal proposedCompaRatio,
        CompensationChangeReason changeReason,
        ProposalStatus status,
        String note,
        UUID appliedRecordId
    ) {
    }

    /**
     * Manager input. Provide either proposedPercent or proposedIncreaseAmount
     * (percent wins if both set); null/zero both clears the proposal back to
     * PENDING. changeReason defaults to MERIT.
     */
    public record ProposalUpdateDto(
        @PositiveOrZero BigDecimal proposedPercent,
        @PositiveOrZero BigDecimal proposedIncreaseAmount,
        CompensationChangeReason changeReason,
        @Size(max = 2000) String note
    ) {
    }
}
