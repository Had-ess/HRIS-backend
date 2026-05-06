package com.hris.auth.repository;

import com.hris.auth.entity.Department;
import com.hris.auth.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByCode(String code);

    boolean existsByHeadEmployeeId(UUID headEmployeeId);

    Page<Department> findAllByIdIn(Collection<UUID> ids, Pageable pageable);

    @Query("SELECT d.headEmployee FROM Department d WHERE d.id = :deptId")
    Optional<Employee> findDepartmentHead(@Param("deptId") UUID departmentId);
}
