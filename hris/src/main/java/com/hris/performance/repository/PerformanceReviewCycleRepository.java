package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceReviewCycle;
import com.hris.performance.enums.CycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceReviewCycleRepository extends JpaRepository<PerformanceReviewCycle, UUID> {

    List<PerformanceReviewCycle> findAllByOrderByCreatedAtDesc();

    List<PerformanceReviewCycle> findByStatus(CycleStatus status);

    // Job sweep finders.
    List<PerformanceReviewCycle> findByStatusAndOpensOnLessThanEqual(CycleStatus status, LocalDate date);

    List<PerformanceReviewCycle> findByStatusAndSelfAssessmentDueLessThan(CycleStatus status, LocalDate date);

    List<PerformanceReviewCycle> findByStatusAndClosesOnLessThanEqual(CycleStatus status, LocalDate date);
}
