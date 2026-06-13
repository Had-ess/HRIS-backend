package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceRatingLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceRatingLevelRepository extends JpaRepository<PerformanceRatingLevel, UUID> {

    List<PerformanceRatingLevel> findByScaleIdOrderByDisplayOrderAsc(UUID scaleId);

    void deleteByScaleId(UUID scaleId);
}
