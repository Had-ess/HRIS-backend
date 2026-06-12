package com.hris.timesheet.repository;

import com.hris.timesheet.entity.Timesheet;
import com.hris.timesheet.enums.TimesheetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimesheetRepository extends JpaRepository<Timesheet, UUID> {

    Optional<Timesheet> findByEmployeeIdAndPeriodStart(UUID employeeId, LocalDate periodStart);

    List<Timesheet> findByEmployeeIdAndPeriodStartBetweenOrderByPeriodStartDesc(
        UUID employeeId, LocalDate from, LocalDate to);

    List<Timesheet> findByStatusOrderBySubmittedAtAsc(TimesheetStatus status);

    @Query("""
        SELECT t FROM Timesheet t, Employee e
        WHERE e.id = t.employeeId
          AND t.status = :status
          AND (e.departmentId IN :departmentIds OR e.supervisorEmployeeId = :approverEmployeeId)
        ORDER BY t.submittedAt ASC
        """)
    List<Timesheet> findPendingForDepartmentsOrSupervisor(
        @Param("status") TimesheetStatus status,
        @Param("departmentIds") Collection<UUID> departmentIds,
        @Param("approverEmployeeId") UUID approverEmployeeId);

    @Query("""
        SELECT t FROM Timesheet t, Employee e
        WHERE e.id = t.employeeId
          AND t.status = :status
          AND e.supervisorEmployeeId = :approverEmployeeId
        ORDER BY t.submittedAt ASC
        """)
    List<Timesheet> findPendingForSupervisor(
        @Param("status") TimesheetStatus status,
        @Param("approverEmployeeId") UUID approverEmployeeId);
}
