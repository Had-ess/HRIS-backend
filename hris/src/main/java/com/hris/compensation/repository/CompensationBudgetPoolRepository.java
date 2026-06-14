package com.hris.compensation.repository;

import com.hris.compensation.entity.CompensationBudgetPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompensationBudgetPoolRepository extends JpaRepository<CompensationBudgetPool, UUID> {

    List<CompensationBudgetPool> findByCycleId(UUID cycleId);

    Optional<CompensationBudgetPool> findByCycleIdAndDepartmentId(UUID cycleId, UUID departmentId);

    boolean existsByCycleId(UUID cycleId);
}
