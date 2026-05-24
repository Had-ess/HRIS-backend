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

    long countByDepartmentId(UUID departmentId);

    List<Employee> findByDepartmentId(UUID departmentId);

    List<Employee> findBySupervisorEmployeeId(UUID supervisorEmployeeId);

    Page<Employee> findByDepartmentId(UUID departmentId, Pageable pageable);

    Page<Employee> findByDepartmentIdIn(List<UUID> departmentIds, Pageable pageable);

    Page<Employee> findAll(Pageable pageable);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.hireDate >= :since")
    long countHiredAfter(@Param("since") java.time.LocalDate since);
}

