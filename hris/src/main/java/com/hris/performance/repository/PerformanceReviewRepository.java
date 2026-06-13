package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceReview;
import com.hris.performance.enums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {

    Optional<PerformanceReview> findByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);

    boolean existsByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);

    List<PerformanceReview> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<PerformanceReview> findByReviewerEmployeeIdOrderByCreatedAtDesc(UUID reviewerEmployeeId);

    List<PerformanceReview> findByCycleId(UUID cycleId);

    List<PerformanceReview> findByCycleIdAndStatusIn(UUID cycleId, Collection<ReviewStatus> statuses);

    long countByCycleId(UUID cycleId);

    long countByCycleIdAndStatus(UUID cycleId, ReviewStatus status);
}
