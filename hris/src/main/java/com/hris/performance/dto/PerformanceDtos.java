package com.hris.performance.dto;

import com.hris.performance.enums.CompetencyCategory;
import com.hris.performance.enums.CycleStatus;
import com.hris.performance.enums.CycleType;
import com.hris.performance.enums.FeedbackRequestStatus;
import com.hris.performance.enums.GoalCategory;
import com.hris.performance.enums.GoalStatus;
import com.hris.performance.enums.ReviewStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
        UUID potentialRatingLevelId,
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
        UUID potentialRatingLevelId,
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

    // --- 360 / peer feedback ---

    /** An active employee eligible to be nominated as a rater (picker option). */
    public record FeedbackCandidateDto(
        UUID employeeId,
        String name
    ) {
    }

    /** A single competency line on a feedback request (snapshot + the rater's rating). */
    public record FeedbackCompetencyRatingDto(
        UUID id,
        UUID competencyId,
        String competencyName,
        CompetencyCategory category,
        UUID ratingLevelId,
        int displayOrder
    ) {
    }

    /** Attributed view of one rater's request — reviewer + HR only. */
    public record FeedbackRequestDto(
        UUID id,
        UUID reviewId,
        UUID raterEmployeeId,
        String raterName,
        FeedbackRequestStatus status,
        String strengths,
        String improvements,
        Instant submittedAt,
        List<FeedbackCompetencyRatingDto> competencyRatings
    ) {
    }

    /** The rater's working copy of a request they were nominated for (their inbox). */
    public record MyFeedbackRequestDto(
        UUID id,
        UUID reviewId,
        String subjectName,
        String cycleName,
        FeedbackRequestStatus status,
        String strengths,
        String improvements,
        List<RatingLevelDto> scaleLevels,
        List<FeedbackCompetencyRatingDto> competencyRatings
    ) {
    }

    /** Anonymized aggregate the subject sees — identities hidden, SUBMITTED only. */
    public record FeedbackAggregateDto(
        int responseCount,
        int pendingCount,
        List<FeedbackAggregateCompetencyDto> competencies,
        List<String> strengths,
        List<String> improvements
    ) {
    }

    public record FeedbackAggregateCompetencyDto(
        UUID competencyId,
        String competencyName,
        CompetencyCategory category,
        Double averageRating,
        int ratedCount
    ) {
    }

    public record FeedbackNominateDto(
        @NotEmpty List<@NotNull UUID> raterEmployeeIds
    ) {
    }

    public record FeedbackCompetencyRatingInput(
        @NotNull UUID feedbackCompetencyRatingId,
        UUID ratingLevelId
    ) {
    }

    public record FeedbackSubmitDto(
        @Size(max = 5000) String strengths,
        @Size(max = 5000) String improvements,
        @Valid List<FeedbackCompetencyRatingInput> competencyRatings
    ) {
    }

    // --- 9-box calibration ---

    /** One review's placement on the grid. A band is 1 (Low) / 2 (Mid) / 3 (High), or null if unset. */
    public record CalibrationReviewDto(
        UUID reviewId,
        UUID employeeId,
        String employeeName,
        String jobTitle,
        UUID departmentId,
        ReviewStatus status,
        UUID performanceLevelId,
        String performanceLabel,
        Integer performanceValue,
        Integer performanceBand,
        UUID potentialLevelId,
        String potentialLabel,
        Integer potentialValue,
        Integer potentialBand,
        boolean adjusted
    ) {
    }

    /** The per-cycle 9-box: scale legend, placed reviews (both axes set), and unplaced ones. */
    public record CalibrationGridDto(
        UUID cycleId,
        String cycleName,
        CycleStatus cycleStatus,
        List<RatingLevelDto> scaleLevels,
        List<CalibrationReviewDto> placed,
        List<CalibrationReviewDto> unplaced
    ) {
    }

    /** An HR move: the target cell's bands (1-3) plus an optional calibration note. */
    public record CalibrationAdjustDto(
        @NotNull @Min(1) @Max(3) Integer performanceBand,
        @NotNull @Min(1) @Max(3) Integer potentialBand,
        @Size(max = 2000) String note
    ) {
    }
}
