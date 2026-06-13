package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceReviewCompetency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceReviewCompetencyRepository
        extends JpaRepository<PerformanceReviewCompetency, UUID> {

    List<PerformanceReviewCompetency> findByReviewIdOrderByDisplayOrderAsc(UUID reviewId);

    boolean existsByReviewId(UUID reviewId);

    boolean existsByCompetencyId(UUID competencyId);
}
