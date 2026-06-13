package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceCompetency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceCompetencyRepository extends JpaRepository<PerformanceCompetency, UUID> {

    List<PerformanceCompetency> findAllByOrderByNameAsc();

    List<PerformanceCompetency> findByIsActiveTrueOrderByNameAsc();

    List<PerformanceCompetency> findByIsActiveTrueAndIsCoreTrue();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
