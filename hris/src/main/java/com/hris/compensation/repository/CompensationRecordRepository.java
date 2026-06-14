package com.hris.compensation.repository;

import com.hris.compensation.entity.CompensationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, UUID> {

    List<CompensationRecord> findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(UUID employeeId);

    Optional<CompensationRecord> findByEmployeeIdAndIsCurrentTrue(UUID employeeId);

    boolean existsByPayGradeId(UUID payGradeId);
}
