package com.hris.compensation.repository;

import com.hris.compensation.entity.CompensationReviewCycleDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompensationReviewCycleDepartmentRepository
        extends JpaRepository<CompensationReviewCycleDepartment, UUID> {

    List<CompensationReviewCycleDepartment> findByCycleId(UUID cycleId);

    void deleteByCycleId(UUID cycleId);
}
