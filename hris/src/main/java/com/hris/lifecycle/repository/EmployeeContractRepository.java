package com.hris.lifecycle.repository;

import com.hris.lifecycle.entity.EmployeeContract;
import com.hris.lifecycle.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeContractRepository extends JpaRepository<EmployeeContract, UUID> {

    List<EmployeeContract> findByEmployeeIdOrderByStartDateDescCreatedAtDesc(UUID employeeId);

    Optional<EmployeeContract> findByEmployeeIdAndStatus(UUID employeeId, ContractStatus status);

    /** ACTIVE contracts whose end date falls on or before the horizon (expiry scan). */
    List<EmployeeContract> findByStatusAndEndDateLessThanEqual(ContractStatus status, LocalDate horizon);

    /** ACTIVE contracts whose probation ends on or before the horizon (probation scan). */
    List<EmployeeContract> findByStatusAndProbationEndDateLessThanEqual(ContractStatus status, LocalDate horizon);
}
