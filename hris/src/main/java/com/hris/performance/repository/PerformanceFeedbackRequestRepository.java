package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceFeedbackRequest;
import com.hris.performance.enums.FeedbackRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceFeedbackRequestRepository
        extends JpaRepository<PerformanceFeedbackRequest, UUID> {

    List<PerformanceFeedbackRequest> findByReviewIdOrderByCreatedAtAsc(UUID reviewId);

    List<PerformanceFeedbackRequest> findByRaterEmployeeIdOrderByCreatedAtDesc(UUID raterEmployeeId);

    List<PerformanceFeedbackRequest> findByRaterEmployeeIdAndStatusOrderByCreatedAtDesc(
        UUID raterEmployeeId, FeedbackRequestStatus status);

    List<PerformanceFeedbackRequest> findByCycleIdAndStatus(UUID cycleId, FeedbackRequestStatus status);

    boolean existsByReviewIdAndRaterEmployeeId(UUID reviewId, UUID raterEmployeeId);
}
