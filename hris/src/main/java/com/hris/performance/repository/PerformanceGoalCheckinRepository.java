package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceGoalCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceGoalCheckinRepository extends JpaRepository<PerformanceGoalCheckin, UUID> {

    List<PerformanceGoalCheckin> findByGoalIdOrderByCreatedAtDesc(UUID goalId);
}
