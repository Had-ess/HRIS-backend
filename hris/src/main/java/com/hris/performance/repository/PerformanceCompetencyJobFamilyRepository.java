package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceCompetencyJobFamily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceCompetencyJobFamilyRepository
        extends JpaRepository<PerformanceCompetencyJobFamily, UUID> {

    List<PerformanceCompetencyJobFamily> findByCompetencyId(UUID competencyId);

    List<PerformanceCompetencyJobFamily> findByCompetencyIdIn(List<UUID> competencyIds);

    List<PerformanceCompetencyJobFamily> findByJobFamily(String jobFamily);

    void deleteByCompetencyId(UUID competencyId);
}
