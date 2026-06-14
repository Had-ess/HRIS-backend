package com.hris.compensation.repository;

import com.hris.compensation.entity.BonusPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BonusPoolRepository extends JpaRepository<BonusPool, UUID> {

    List<BonusPool> findByCycleId(UUID cycleId);

    Optional<BonusPool> findByCycleIdAndDepartmentId(UUID cycleId, UUID departmentId);

    boolean existsByCycleId(UUID cycleId);
}
