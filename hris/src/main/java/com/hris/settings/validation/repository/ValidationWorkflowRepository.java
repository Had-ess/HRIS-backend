package com.hris.settings.validation.repository;

import com.hris.settings.validation.entity.ValidationUsage;
import com.hris.settings.validation.entity.ValidationWorkflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ValidationWorkflowRepository extends JpaRepository<ValidationWorkflow, UUID> {

    Page<ValidationWorkflow> findAllByOrderByCodeAsc(Pageable pageable);

    List<ValidationWorkflow> findByUsageOrderByCodeAsc(ValidationUsage usage);

    Optional<ValidationWorkflow> findByCodeIgnoreCase(String code);

    Optional<ValidationWorkflow> findFirstByUsageAndActiveTrueAndDefaultWorkflowTrue(ValidationUsage usage);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    boolean existsByUsageAndDefaultWorkflowTrueAndIdNot(ValidationUsage usage, UUID id);
}
