package com.hris.leave.repository;

import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.enums.LeaveStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.id = :id")
    java.util.Optional<LeaveRequest> findByIdForUpdate(@Param("id") UUID id);

    Page<LeaveRequest> findByEmployeeIdOrderBySubmittedAtDesc(UUID employeeId, Pageable pageable);
    Page<LeaveRequest> findByEmployeeIdAndStatusOrderBySubmittedAtDesc(UUID employeeId, LeaveStatus status, Pageable pageable);
    Page<LeaveRequest> findAllByOrderBySubmittedAtDesc(Pageable pageable);
    Page<LeaveRequest> findByStatusOrderBySubmittedAtDesc(LeaveStatus status, Pageable pageable);
    List<LeaveRequest> findByEmployeeId(UUID employeeId);
    List<LeaveRequest> findTop5ByEmployeeIdOrderBySubmittedAtDesc(UUID employeeId);
    boolean existsByEmployeeId(UUID employeeId);

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

    @Query("""
        SELECT COUNT(DISTINCT lr.id) FROM LeaveRequest lr
        JOIN ProjectAssignment pa ON pa.employeeId = lr.employeeId
        WHERE pa.supervisorId = :supervisorId
          AND pa.isActive = true
          AND pa.startDate <= lr.endDate
          AND (pa.endDate IS NULL OR pa.endDate >= lr.startDate)
          AND lr.status = :status
          AND lr.startDate >= :startDate
        """)
    long countUpcomingSupervisorRequests(
        @Param("supervisorId") UUID supervisorEmployeeId,
        @Param("status") LeaveStatus status,
        @Param("startDate") LocalDate startDate);

    @Query("""
        SELECT lr
        FROM LeaveRequest lr
        JOIN Employee e ON e.id = lr.employeeId
        WHERE e.departmentId = :departmentId
        ORDER BY lr.submittedAt DESC
        """)
    Page<LeaveRequest> findByDepartmentIdOrderBySubmittedAtDesc(
        @Param("departmentId") UUID departmentId,
        Pageable pageable);

    @Query("""
        SELECT lr
        FROM LeaveRequest lr
        JOIN Employee e ON e.id = lr.employeeId
        WHERE e.departmentId = :departmentId
          AND lr.status = :status
        ORDER BY lr.submittedAt DESC
        """)
    Page<LeaveRequest> findByDepartmentIdAndStatusOrderBySubmittedAtDesc(
        @Param("departmentId") UUID departmentId,
        @Param("status") LeaveStatus status,
        Pageable pageable);

    @Query("""
        SELECT lr
        FROM LeaveRequest lr
        JOIN Employee e ON e.id = lr.employeeId
        WHERE e.departmentId = :departmentId
          AND lr.employeeId = :employeeId
        ORDER BY lr.submittedAt DESC
        """)
    Page<LeaveRequest> findByDepartmentIdAndEmployeeIdOrderBySubmittedAtDesc(
        @Param("departmentId") UUID departmentId,
        @Param("employeeId") UUID employeeId,
        Pageable pageable);

    @Query("""
        SELECT lr
        FROM LeaveRequest lr
        JOIN Employee e ON e.id = lr.employeeId
        WHERE e.departmentId = :departmentId
          AND lr.employeeId = :employeeId
          AND lr.status = :status
        ORDER BY lr.submittedAt DESC
        """)
    Page<LeaveRequest> findByDepartmentIdAndEmployeeIdAndStatusOrderBySubmittedAtDesc(
        @Param("departmentId") UUID departmentId,
        @Param("employeeId") UUID employeeId,
        @Param("status") LeaveStatus status,
        Pageable pageable);

    @Query("""
        SELECT COUNT(lr) FROM LeaveRequest lr
        WHERE lr.status = :status
          AND lr.startDate <= :date
          AND lr.endDate >= :date
        """)
    long countOnLeaveOnDate(@Param("date") LocalDate date, @Param("status") LeaveStatus status);

    @Query("""
        SELECT lt.name, lt.code, SUM(lr.workingDays)
        FROM LeaveRequest lr
        JOIN LeaveType lt ON lt.id = lr.leaveTypeId
        WHERE YEAR(lr.startDate) = :year
          AND lr.status = 'APPROVED'
        GROUP BY lt.id, lt.name, lt.code
        ORDER BY SUM(lr.workingDays) DESC
        """)
    List<Object[]> sumWorkingDaysByLeaveTypeForYear(@Param("year") int year);
}

