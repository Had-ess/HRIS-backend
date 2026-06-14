package com.hris.compensation.repository;

import com.hris.compensation.entity.BonusPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BonusPlanRepository extends JpaRepository<BonusPlan, UUID> {

    List<BonusPlan> findAllByOrderByCodeAsc();

    List<BonusPlan> findByIsActiveTrueOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
