package com.hris.leave.repository;

import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.enums.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    Page<LeaveRequest> findByEmployeeId(UUID employeeId, Pageable pageable);
    Page<LeaveRequest> findByEmployeeIdAndStatus(UUID employeeId, LeaveStatus status, Pageable pageable);
    List<LeaveRequest> findByEmployeeId(UUID employeeId);
    List<LeaveRequest> findTop5ByEmployeeIdOrderBySubmittedAtDesc(UUID employeeId);

    @Query("""
        SELECT COUNT(lr) FROM LeaveRequest lr
        JOIN Employee e ON e.id = lr.employeeId
        WHERE e.departmentId = :departmentId
          AND lr.status = :status
          AND lr.startDate >= :startDate
        """)
    long countUpcomingDepartmentRequests(
        @Param("departmentId") UUID departmentId,
        @Param("status") LeaveStatus status,
        @Param("startDate") LocalDate startDate);
}
