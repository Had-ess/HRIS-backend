package com.hris.performance.repository;

import com.hris.performance.entity.PerformanceRatingScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerformanceRatingScaleRepository extends JpaRepository<PerformanceRatingScale, UUID> {

    List<PerformanceRatingScale> findAllByOrderByNameAsc();

    List<PerformanceRatingScale> findByIsActiveTrueOrderByNameAsc();

    Optional<PerformanceRatingScale> findFirstByIsDefaultTrue();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
