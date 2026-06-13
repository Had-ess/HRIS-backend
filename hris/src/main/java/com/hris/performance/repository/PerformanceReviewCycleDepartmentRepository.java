package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceReviewCycleDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceReviewCycleDepartmentRepository
        extends JpaRepository<PerformanceReviewCycleDepartment, PerformanceReviewCycleDepartment.Pk> {

    List<PerformanceReviewCycleDepartment> findByCycleId(UUID cycleId);

    void deleteByCycleId(UUID cycleId);
}
