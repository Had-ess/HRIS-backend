package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceGoal;
import com.hris.performance.enums.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceGoalRepository extends JpaRepository<PerformanceGoal, UUID> {

    List<PerformanceGoal> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<PerformanceGoal> findByEmployeeIdAndCycleId(UUID employeeId, UUID cycleId);

    List<PerformanceGoal> findByEmployeeIdAndCycleIdAndStatus(UUID employeeId, UUID cycleId, GoalStatus status);
}
