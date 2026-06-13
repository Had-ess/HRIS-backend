package com.hris.performance.service;

import com.hris.auth.entity.Employee;
import com.hris.auth.repository.EmployeeRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.performance.dto.PerformanceDtos.CheckinCreateDto;
import com.hris.performance.dto.PerformanceDtos.CheckinDto;
import com.hris.performance.dto.PerformanceDtos.GoalCreateDto;
import com.hris.performance.dto.PerformanceDtos.GoalDto;
import com.hris.performance.dto.PerformanceDtos.GoalRatingInput;
import com.hris.performance.dto.PerformanceDtos.GoalUpdateDto;
import com.hris.performance.entity.PerformanceGoal;
import com.hris.performance.entity.PerformanceGoalCheckin;
import com.hris.performance.entity.PerformanceRatingLevel;
import com.hris.performance.enums.GoalStatus;
import com.hris.performance.repository.PerformanceGoalCheckinRepository;
import com.hris.performance.repository.PerformanceGoalRepository;
import com.hris.performance.repository.PerformanceRatingLevelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Weighted goals + append-only check-ins. Owns the weight rule (active weights per
 * (employee, cycle) must sum to 100 at self-submit) and the score computation
 * (Σ weight × rated level value / 100), both consumed by the review service.
 */
@Service
@RequiredArgsConstructor
public class PerformanceGoalService {

    private final PerformanceGoalRepository goalRepository;
    private final PerformanceGoalCheckinRepository checkinRepository;
    private final PerformanceRatingLevelRepository levelRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<GoalDto> getMyGoals(UUID userId, UUID cycleId) {
        Employee me = employee(userId);
        List<PerformanceGoal> goals = cycleId != null
            ? goalRepository.findByEmployeeIdAndCycleId(me.getId(), cycleId)
            : goalRepository.findByEmployeeIdOrderByCreatedAtDesc(me.getId());
        return goals.stream().map(this::toDto).toList();
    }

    @Transactional
    public GoalDto createGoal(UUID userId, GoalCreateDto dto) {
        Employee me = employee(userId);
        PerformanceGoal goal = goalRepository.save(PerformanceGoal.builder()
            .employeeId(me.getId())
            .cycleId(dto.cycleId())
            .title(dto.title().trim())
            .description(dto.description())
            .category(dto.category())
            .weight(dto.weight())
            .status(GoalStatus.ACTIVE)
            .progressPct(0)
            .dueDate(dto.dueDate())
            .createdBy(userId)
            .build());
        return toDto(goal);
    }

    @Transactional
    public GoalDto updateGoal(UUID userId, UUID goalId, GoalUpdateDto dto) {
        Employee me = employee(userId);
        PerformanceGoal goal = ownedGoal(goalId, me.getId());
        goal.setTitle(dto.title().trim());
        goal.setDescription(dto.description());
        goal.setCategory(dto.category());
        goal.setWeight(dto.weight());
        if (dto.status() != null) {
            goal.setStatus(dto.status());
        }
        if (dto.progressPct() != null && dto.progressPct() != goal.getProgressPct()) {
            goal.setProgressPct(dto.progressPct());
            // A progress change is logged as a check-in (append-only history).
            checkinRepository.save(PerformanceGoalCheckin.builder()
                .goalId(goalId)
                .authorEmployeeId(me.getId())
                .note(null)
                .progressPct(dto.progressPct())
                .build());
        }
        goalRepository.save(goal);
        return toDto(goal);
    }

    @Transactional
    public void deleteGoal(UUID userId, UUID goalId) {
        Employee me = employee(userId);
        PerformanceGoal goal = ownedGoal(goalId, me.getId());
        goalRepository.delete(goal);
    }

    @Transactional
    public CheckinDto addCheckin(UUID userId, UUID goalId, CheckinCreateDto dto) {
        Employee me = employee(userId);
        PerformanceGoal goal = ownedGoal(goalId, me.getId());
        goal.setProgressPct(dto.progressPct());
        goalRepository.save(goal);
        PerformanceGoalCheckin checkin = checkinRepository.save(PerformanceGoalCheckin.builder()
            .goalId(goalId)
            .authorEmployeeId(me.getId())
            .note(dto.note())
            .progressPct(dto.progressPct())
            .build());
        return toCheckinDto(checkin);
    }

    @Transactional(readOnly = true)
    public List<CheckinDto> getCheckins(UUID userId, UUID goalId) {
        Employee me = employee(userId);
        ownedGoal(goalId, me.getId());
        return checkinRepository.findByGoalIdOrderByCreatedAtDesc(goalId).stream()
            .map(this::toCheckinDto).toList();
    }

    /** Goals for a given employee+cycle (used to render a review document). */
    @Transactional(readOnly = true)
    public List<GoalDto> getGoalsForEmployeeCycle(UUID employeeId, UUID cycleId) {
        List<PerformanceGoal> goals = cycleId != null
            ? goalRepository.findByEmployeeIdAndCycleId(employeeId, cycleId)
            : goalRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        return goals.stream().map(this::toDto).toList();
    }

    // --- Consumed by the review service ---

    /** Active goal weights for (employee, cycle) must sum to exactly 100 before self-submit. */
    public void validateWeightsForSubmit(UUID employeeId, UUID cycleId) {
        int sum = activeGoals(employeeId, cycleId).stream().mapToInt(PerformanceGoal::getWeight).sum();
        if (sum != 100) {
            throw new IllegalStateException(
                "Goal weights for this cycle must sum to 100 before submitting (currently " + sum + ")");
        }
    }

    /** Applies the manager's per-goal ratings; each goal must belong to (employee, cycle). */
    @Transactional
    public void applyGoalRatings(UUID employeeId, UUID cycleId, List<GoalRatingInput> ratings) {
        if (ratings == null) {
            return;
        }
        for (GoalRatingInput rating : ratings) {
            PerformanceGoal goal = goalRepository.findById(rating.goalId())
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));
            if (!employeeId.equals(goal.getEmployeeId()) || !java.util.Objects.equals(cycleId, goal.getCycleId())) {
                throw new IllegalArgumentException("Goal does not belong to this review");
            }
            levelRepository.findById(rating.ratingLevelId())
                .orElseThrow(() -> new EntityNotFoundException("Rating level not found"));
            goal.setRatingLevelId(rating.ratingLevelId());
            goalRepository.save(goal);
        }
    }

    /** computed_score = Σ(weight × rated level numeric_value) / 100 over rated active goals. */
    @Transactional(readOnly = true)
    public BigDecimal computeScore(UUID employeeId, UUID cycleId) {
        int weightedSum = 0;
        boolean anyRated = false;
        for (PerformanceGoal goal : activeGoals(employeeId, cycleId)) {
            if (goal.getRatingLevelId() == null) {
                continue;
            }
            PerformanceRatingLevel level = levelRepository.findById(goal.getRatingLevelId()).orElse(null);
            if (level == null) {
                continue;
            }
            weightedSum += goal.getWeight() * level.getNumericValue();
            anyRated = true;
        }
        if (!anyRated) {
            return null;
        }
        return BigDecimal.valueOf(weightedSum).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private List<PerformanceGoal> activeGoals(UUID employeeId, UUID cycleId) {
        return goalRepository.findByEmployeeIdAndCycleIdAndStatus(employeeId, cycleId, GoalStatus.ACTIVE);
    }

    private Employee employee(UUID userId) {
        return employeeRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    }

    private PerformanceGoal ownedGoal(UUID goalId, UUID employeeId) {
        PerformanceGoal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new EntityNotFoundException("Goal not found"));
        if (!employeeId.equals(goal.getEmployeeId())) {
            throw new IllegalArgumentException("You can only modify your own goals");
        }
        return goal;
    }

    GoalDto toDto(PerformanceGoal goal) {
        List<CheckinDto> checkins = checkinRepository.findByGoalIdOrderByCreatedAtDesc(goal.getId()).stream()
            .map(this::toCheckinDto).toList();
        return new GoalDto(goal.getId(), goal.getEmployeeId(), goal.getCycleId(), goal.getTitle(),
            goal.getDescription(), goal.getCategory(), goal.getWeight(), goal.getStatus(),
            goal.getProgressPct(), goal.getDueDate(), goal.getRatingLevelId(), checkins);
    }

    private CheckinDto toCheckinDto(PerformanceGoalCheckin checkin) {
        return new CheckinDto(checkin.getId(), checkin.getAuthorEmployeeId(), checkin.getNote(),
            checkin.getProgressPct(), checkin.getCreatedAt());
    }
}
