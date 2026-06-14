package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceCalibrationAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceCalibrationAdjustmentRepository
        extends JpaRepository<PerformanceCalibrationAdjustment, UUID> {

    List<PerformanceCalibrationAdjustment> findByReviewIdOrderByCreatedAtDesc(UUID reviewId);

    boolean existsByReviewId(UUID reviewId);
}
