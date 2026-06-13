package com.hris.analytics.repository;

import com.hris.analytics.entity.PerformanceFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PerformanceFactRepository extends JpaRepository<PerformanceFact, UUID> {

    boolean existsByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);
}
