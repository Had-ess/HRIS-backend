package com.hris.compensation.repository;

import com.hris.compensation.entity.BonusCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BonusCycleRepository extends JpaRepository<BonusCycle, UUID> {

    List<BonusCycle> findAllByOrderByCreatedAtDesc();

    boolean existsByBonusPlanId(UUID bonusPlanId);
}
