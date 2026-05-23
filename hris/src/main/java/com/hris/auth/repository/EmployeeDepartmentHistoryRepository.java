package com.hris.auth.repository;

import com.hris.auth.entity.EmployeeDepartmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmployeeDepartmentHistoryRepository extends JpaRepository<EmployeeDepartmentHistory, UUID> {
    void deleteByEmployeeId(UUID employeeId);
}
