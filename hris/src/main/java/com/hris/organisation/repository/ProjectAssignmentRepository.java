package com.hris.organisation.repository;

import com.hris.organisation.entity.ProjectAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pa FROM ProjectAssignment pa WHERE pa.id = :id")
    java.util.Optional<ProjectAssignment> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        SELECT pa FROM ProjectAssignment pa
        WHERE pa.employeeId = :eid
          AND pa.startDate <= :endDate
          AND (pa.endDate >= :startDate OR pa.endDate IS NULL)
          AND pa.isActive = true
        """)
    List<ProjectAssignment> findActiveAssignmentsDuringPeriod(
        @Param("eid") UUID employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Modifying
    @Query("UPDATE ProjectAssignment pa SET pa.isActive = false WHERE pa.endDate < CURRENT_DATE AND pa.isActive = true")
    int deactivateExpired();

    List<ProjectAssignment> findByEmployeeIdAndIsActiveTrue(UUID employeeId);

    List<ProjectAssignment> findByProjectId(UUID projectId);

    @Query("""
        SELECT COUNT(pa) FROM ProjectAssignment pa
        WHERE pa.employeeId = :employeeId
          AND pa.projectId = :projectId
          AND pa.isActive = true
          AND (:endDate IS NULL OR pa.startDate <= :endDate)
          AND (pa.endDate IS NULL OR pa.endDate >= :startDate)
        """)
    long countOverlappingActiveAssignments(
        @Param("employeeId") UUID employeeId,
        @Param("projectId") UUID projectId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
}
