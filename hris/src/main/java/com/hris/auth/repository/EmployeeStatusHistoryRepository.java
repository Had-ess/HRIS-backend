package com.hris.auth.repository;

import com.hris.auth.entity.EmployeeStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmployeeStatusHistoryRepository extends JpaRepository<EmployeeStatusHistory, UUID> {
    void deleteByEmployeeId(UUID employeeId);
}
