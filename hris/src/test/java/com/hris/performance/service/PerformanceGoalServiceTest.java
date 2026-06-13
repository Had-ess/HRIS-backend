package com.hris.performance.service;

import com.hris.auth.repository.EmployeeRepository;
import com.hris.performance.dto.PerformanceDtos.GoalRatingInput;
import com.hris.performance.entity.PerformanceGoal;
import com.hris.performance.entity.PerformanceRatingLevel;
import com.hris.performance.enums.GoalStatus;
import com.hris.performance.repository.PerformanceGoalCheckinRepository;
import com.hris.performance.repository.PerformanceGoalRepository;
import com.hris.performance.repository.PerformanceRatingLevelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceGoalServiceTest {

    @Mock private PerformanceGoalRepository goalRepository;
    @Mock private PerformanceGoalCheckinRepository checkinRepository;
    @Mock private PerformanceRatingLevelRepository levelRepository;
    @Mock private EmployeeRepository employeeRepository;

    @InjectMocks private PerformanceGoalService service;

    private PerformanceGoal goal(UUID employeeId, UUID cycleId, int weight, UUID ratingLevelId) {
        return PerformanceGoal.builder().id(UUID.randomUUID()).employeeId(employeeId).cycleId(cycleId)
            .weight(weight).status(GoalStatus.ACTIVE).ratingLevelId(ratingLevelId).build();
    }

    @Test
    @DisplayName("self-submit weight rule passes when active weights sum to exactly 100")
    void validateWeights_passesAt100() {
        UUID emp = UUID.randomUUID();
        UUID cycle = UUID.randomUUID();
        when(goalRepository.findByEmployeeIdAndCycleIdAndStatus(emp, cycle, GoalStatus.ACTIVE))
            .thenReturn(List.of(goal(emp, cycle, 60, null), goal(emp, cycle, 40, null)));
        assertThatCode(() -> service.validateWeightsForSubmit(emp, cycle)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("self-submit weight rule fails when active weights do not sum to 100")
    void validateWeights_failsWhenNot100() {
        UUID emp = UUID.randomUUID();
        UUID cycle = UUID.randomUUID();
        when(goalRepository.findByEmployeeIdAndCycleIdAndStatus(emp, cycle, GoalStatus.ACTIVE))
            .thenReturn(List.of(goal(emp, cycle, 60, null), goal(emp, cycle, 20, null)));
        assertThatThrownBy(() -> service.validateWeightsForSubmit(emp, cycle))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("100");
    }

    @Test
    @DisplayName("score = sum(weight x level value) / 100 over rated goals")
    void computeScore_weightedAverage() {
        UUID emp = UUID.randomUUID();
        UUID cycle = UUID.randomUUID();
        UUID levelHigh = UUID.randomUUID();
        UUID levelLow = UUID.randomUUID();
        when(goalRepository.findByEmployeeIdAndCycleIdAndStatus(emp, cycle, GoalStatus.ACTIVE))
            .thenReturn(List.of(goal(emp, cycle, 50, levelHigh), goal(emp, cycle, 50, levelLow)));
        when(levelRepository.findById(levelHigh)).thenReturn(Optional.of(
            PerformanceRatingLevel.builder().id(levelHigh).numericValue(4).build()));
        when(levelRepository.findById(levelLow)).thenReturn(Optional.of(
            PerformanceRatingLevel.builder().id(levelLow).numericValue(2).build()));

        // (50*4 + 50*2) / 100 = 3.00
        assertThat(service.computeScore(emp, cycle)).isEqualByComparingTo(new BigDecimal("3.00"));
    }

    @Test
    @DisplayName("score is null when no goals are rated yet")
    void computeScore_nullWhenUnrated() {
        UUID emp = UUID.randomUUID();
        UUID cycle = UUID.randomUUID();
        when(goalRepository.findByEmployeeIdAndCycleIdAndStatus(emp, cycle, GoalStatus.ACTIVE))
            .thenReturn(List.of(goal(emp, cycle, 100, null)));
        assertThat(service.computeScore(emp, cycle)).isNull();
    }

    @Test
    @DisplayName("applying a goal rating rejects goals that do not belong to the review's employee/cycle")
    void applyGoalRatings_rejectsForeignGoal() {
        UUID emp = UUID.randomUUID();
        UUID cycle = UUID.randomUUID();
        UUID otherEmp = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(
            PerformanceGoal.builder().id(goalId).employeeId(otherEmp).cycleId(cycle).build()));
        assertThatThrownBy(() -> service.applyGoalRatings(emp, cycle, List.of(new GoalRatingInput(goalId, levelId))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
