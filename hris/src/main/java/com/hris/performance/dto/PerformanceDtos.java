package com.hris.performance.dto;

import com.hris.performance.enums.CompetencyCategory;
import com.hris.performance.enums.CycleStatus;
import com.hris.performance.enums.CycleType;
import com.hris.performance.enums.GoalCategory;
import com.hris.performance.enums.GoalStatus;
import com.hris.performance.enums.ReviewStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response records of the performance API (see PERFORMANCE_MODULE_DESIGN.md §2-5). */
public final class PerformanceDtos {

    private PerformanceDtos() {
    }

    // --- Rating scales ---

    public record RatingLevelDto(
        UUID id,
        String label,
        int numericValue,
        int displayOrder
    ) {
    }

    public record RatingScaleDto(
        UUID id,
        String name,
        boolean isDefault,
        boolean isActive,
        List<RatingLevelDto> levels
    ) {
    }

    public record RatingLevelInput(
        @NotBlank @Size(max = 100) String label,
        @NotNull Integer numericValue,
        @NotNull Integer displayOrder
    ) {
    }

    public record RatingScaleCreateDto(
        @NotBlank @Size(max = 150) String name,
        Boolean isDefault,
        Boolean isActive,
        @NotNull @Size(min = 2, message = "A scale needs at least two levels") @Valid List<RatingLevelInput> levels
    ) {
    }

    // --- Review cycles ---

    public record CycleDto(
        UUID id,
        String name,
        CycleType cycleType,
        CycleStatus status,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate selfAssessmentDue,
        LocalDate managerReviewDue,
        LocalDate opensOn,
        LocalDate closesOn,
        boolean includeSubDepartments,
        UUID ratingScaleId,
        List<UUID> departmentIds,
        long reviewCount,
        long completedCount
    ) {
    }

    public record CycleCreateDto(
        @NotBlank @Size(max = 200) String name,
        @NotNull CycleType cycleType,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        LocalDate selfAssessmentDue,
        LocalDate managerReviewDue,
        LocalDate opensOn,
        LocalDate closesOn,
        Boolean includeSubDepartments,
        @NotNull UUID ratingScaleId,
        List<UUID> departmentIds
    ) {
    }

    // --- Reviews ---

    public record ReviewGoalDto(
        UUID id,
        String title,
        String description,
        GoalCategory category,
        int weight,
        GoalStatus status,
        int progressPct,
        LocalDate dueDate,
        UUID ratingLevelId,
        List<CheckinDto> checkins
    ) {
    }

    public record ReviewDto(
        UUID id,
        UUID cycleId,
        String cycleName,
        UUID employeeId,
        String employeeName,
        UUID reviewerEmployeeId,
        String reviewerName,
        UUID departmentId,
        String jobTitle,
        ReviewStatus status,
        String selfComments,
        String managerComments,
        UUID overallRatingLevelId,
        BigDecimal computedScore,
        UUID hrOverrideRatingLevelId,
        Instant selfSubmittedAt,
        Instant managerSubmittedAt,
        Instant acknowledgedAt,
        List<RatingLevelDto> scaleLevels,
        List<ReviewGoalDto> goals,
        List<ReviewCompetencyDto> competencies
    ) {
    }

    public record ReviewCompetencyDto(
        UUID id,
        UUID competencyId,
        String competencyName,
        CompetencyCategory category,
        UUID ratingLevelId,
        String comments,
        int displayOrder
    ) {
    }

    public record SelfSubmitDto(
        @Size(max = 5000) String selfComments
    ) {
    }

    public record GoalRatingInput(
        @NotNull UUID goalId,
        @NotNull UUID ratingLevelId
    ) {
    }

    public record ManagerSubmitDto(
        @Size(max = 5000) String managerComments,
        UUID overallRatingLevelId,
        @Valid List<GoalRatingInput> goalRatings,
        @Valid List<CompetencyRatingInput> competencyRatings
    ) {
    }

    public record CompetencyRatingInput(
        @NotNull UUID reviewCompetencyId,
        UUID ratingLevelId,
        @Size(max = 2000) String comments
    ) {
    }

    public record HrOverrideDto(
        @NotNull UUID ratingLevelId
    ) {
    }

    // --- Goals + check-ins ---

    public record CheckinDto(
        UUID id,
        UUID authorEmployeeId,
        String note,
        int progressPct,
        Instant createdAt
    ) {
    }

    public record GoalDto(
        UUID id,
        UUID employeeId,
        UUID cycleId,
        String title,
        String description,
        GoalCategory category,
        int weight,
        GoalStatus status,
        int progressPct,
        LocalDate dueDate,
        UUID ratingLevelId,
        List<CheckinDto> checkins
    ) {
    }

    public record GoalCreateDto(
        UUID cycleId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        GoalCategory category,
        @NotNull @Min(0) @Max(100) Integer weight,
        LocalDate dueDate
    ) {
    }

    public record GoalUpdateDto(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        GoalCategory category,
        @NotNull @Min(0) @Max(100) Integer weight,
        GoalStatus status,
        @Min(0) @Max(100) Integer progressPct,
        LocalDate dueDate
    ) {
    }

    public record CheckinCreateDto(
        @Size(max = 2000) String note,
        @NotNull @Min(0) @Max(100) Integer progressPct
    ) {
    }

    // --- Competencies (per-tenant catalog) ---

    public record CompetencyDto(
        UUID id,
        String name,
        String description,
        CompetencyCategory category,
        boolean isCore,
        boolean isActive,
        List<String> jobFamilies
    ) {
    }

    public record CompetencyCreateDto(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 5000) String description,
        CompetencyCategory category,
        Boolean isCore,
        Boolean isActive,
        List<@NotBlank @Size(max = 150) String> jobFamilies
    ) {
    }
}
