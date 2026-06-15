package com.hris.recruitment.repository;

import com.hris.recruitment.entity.Requisition;
import com.hris.recruitment.enums.RequisitionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequisitionRepository extends JpaRepository<Requisition, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Requisition r WHERE r.id = :id")
    Optional<Requisition> findByIdForUpdate(@Param("id") UUID id);

    List<Requisition> findAllByOrderByCreatedAtDesc();

    List<Requisition> findByStatusOrderByCreatedAtDesc(RequisitionStatus status);

    List<Requisition> findByDepartmentIdOrderByCreatedAtDesc(UUID departmentId);

    List<Requisition> findByHiringManagerEmployeeIdOrderByCreatedAtDesc(UUID hiringManagerEmployeeId);
}
