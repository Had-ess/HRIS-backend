package com.hris.compensation.repository;

import com.hris.compensation.entity.CompensationReviewCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompensationReviewCycleRepository extends JpaRepository<CompensationReviewCycle, UUID> {

    List<CompensationReviewCycle> findAllByOrderByCreatedAtDesc();
}
