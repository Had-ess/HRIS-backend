package com.hris.compensation.dto;

import com.hris.compensation.dto.CompensationDtos.CompensationRecordDto;
import com.hris.compensation.enums.BonusAwardType;
import com.hris.compensation.enums.PayFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Phase 4 read-only DTOs: total-rewards statement + compensation analytics. */
public final class CompensationAnalyticsDtos {

    private CompensationAnalyticsDtos() {
    }

    /** Dimension the HR analytics dashboard groups employees by. */
    public enum GroupBy {
        DEPARTMENT, JOB_FAMILY, GRADE, LOCATION
    }

    /** One variable-pay line in a total-rewards statement (a PAID bonus award). */
    public record VariableLineDto(
        UUID awardId,
        BonusAwardType awardType,
        String planName,
        BigDecimal amount,
        String currency,
        LocalDate payoutDate,
        String note
    ) {
    }

    /** An employee's consolidated rewards for one calendar year. */
    public record TotalRewardsDto(
        UUID employeeId,
        String employeeName,
        int year,
        BigDecimal currentBaseAmount,
        String currency,
        PayFrequency payFrequency,
        BigDecimal annualizedBase,
        String payGradeCode,
        String payGradeName,
        BigDecimal compaRatio,
        List<CompensationRecordDto> baseHistory,
        List<VariableLineDto> variableAwards,
        BigDecimal totalVariable,
        BigDecimal totalCashCompensation
    ) {
    }

    /** Aggregated compensation metrics for one group (or the overall summary). */
    public record AnalyticsGroupDto(
        String label,
        int headcount,
        BigDecimal avgAnnualBase,
        BigDecimal medianAnnualBase,
        BigDecimal avgCompaRatio,
        int gradedCount,
        int belowCompetitive,
        int competitive,
        int aboveCompetitive,
        int belowMinCount,
        int aboveMaxCount,
        BigDecimal totalVariablePaid,
        BigDecimal avgTotalComp
    ) {
    }

    /** The analytics dashboard payload: per-group rows plus an overall summary. */
    public record CompensationAnalyticsDto(
        int year,
        GroupBy groupBy,
        List<AnalyticsGroupDto> groups,
        AnalyticsGroupDto overall
    ) {
    }
}
