package com.hris.auth.repository;

import com.hris.auth.entity.Employee;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") UUID id);

    Optional<Employee> findByUserId(UUID userId);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    boolean existsByDepartmentId(UUID departmentId);

    boolean existsBySupervisorEmployeeId(UUID supervisorEmployeeId);

    boolean existsBySupervisorEmployeeIdAndStatusNot(
        UUID supervisorEmployeeId, com.hris.auth.enums.EmployeeStatus status);

    long countByDepartmentId(UUID departmentId);

    boolean existsByJobTitleId(UUID jobTitleId);

    long countByJobTitleId(UUID jobTitleId);

    /** Re-syncs the denormalized employees.job_title copy after a catalog rename. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Employee e SET e.jobTitle = :name WHERE e.jobTitleId = :jobTitleId")
    int syncJobTitleName(@Param("jobTitleId") UUID jobTitleId, @Param("name") String name);

    List<Employee> findByDepartmentId(UUID departmentId);

    List<Employee> findBySupervisorEmployeeId(UUID supervisorEmployeeId);

    /** Active employees in the tenant (review-cycle generation, all-scope). */
    List<Employee> findByStatus(com.hris.auth.enums.EmployeeStatus status);

    /** Active employees in the given departments (review-cycle generation, scoped). */
    List<Employee> findByDepartmentIdInAndStatus(
        List<UUID> departmentIds, com.hris.auth.enums.EmployeeStatus status);

    Page<Employee> findByDepartmentId(UUID departmentId, Pageable pageable);

    Page<Employee> findByDepartmentIdIn(List<UUID> departmentIds, Pageable pageable);

    Page<Employee> findAll(Pageable pageable);

    /** Scheduled terminations that have become due (lifecycle job). */
    @Query("""
        SELECT e FROM Employee e
        WHERE e.terminationDate IS NOT NULL
          AND e.terminationDate <= :asOf
          AND e.status <> com.hris.auth.enums.EmployeeStatus.TERMINATED
        """)
    List<Employee> findDueScheduledTerminations(@Param("asOf") java.time.LocalDate asOf);

    /** Scheduled transfers that have become due (lifecycle job). */
    @Query("""
        SELECT e FROM Employee e
        WHERE e.scheduledTransferDate IS NOT NULL
          AND e.scheduledTransferDate <= :asOf
          AND e.status <> com.hris.auth.enums.EmployeeStatus.TERMINATED
        """)
    List<Employee> findDueScheduledTransfers(@Param("asOf") java.time.LocalDate asOf);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.hireDate >= :since")
    long countHiredAfter(@Param("since") java.time.LocalDate since);

    @Query("""
        SELECT COUNT(e) FROM Employee e
        WHERE e.hireDate <= :asOf
          AND (e.terminationDate IS NULL OR e.terminationDate > :asOf)
        """)
    long countActiveAsOf(@Param("asOf") java.time.LocalDate asOf);

    @Query("""
        SELECT COUNT(e) FROM Employee e
        WHERE e.terminationDate IS NOT NULL
          AND e.terminationDate >= :from
          AND e.terminationDate < :toExclusive
        """)
    long countTerminatedBetween(
        @Param("from") java.time.LocalDate from,
        @Param("toExclusive") java.time.LocalDate toExclusive);
}

