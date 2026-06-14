package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceFeedbackCompetencyRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceFeedbackCompetencyRatingRepository
        extends JpaRepository<PerformanceFeedbackCompetencyRating, UUID> {

    List<PerformanceFeedbackCompetencyRating> findByFeedbackRequestIdOrderByDisplayOrderAsc(UUID feedbackRequestId);

    List<PerformanceFeedbackCompetencyRating> findByFeedbackRequestIdInOrderByDisplayOrderAsc(
        List<UUID> feedbackRequestIds);
}
