package com.hris.compensation.repository;

import com.hris.compensation.entity.BonusCycleDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BonusCycleDepartmentRepository extends JpaRepository<BonusCycleDepartment, UUID> {

    List<BonusCycleDepartment> findByCycleId(UUID cycleId);

    void deleteByCycleId(UUID cycleId);
}
