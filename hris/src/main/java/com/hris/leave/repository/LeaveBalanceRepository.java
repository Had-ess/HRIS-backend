package com.hris.leave.repository;

import com.hris.leave.dto.LeaveBalanceSummaryDto;
import com.hris.leave.entity.LeaveBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(UUID employeeId, UUID leaveTypeId, int year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT lb FROM LeaveBalance lb
        WHERE lb.employeeId = :employeeId
          AND lb.leaveTypeId = :leaveTypeId
          AND lb.year = :year
        """)
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYearForUpdate(
        @Param("employeeId") UUID employeeId,
        @Param("leaveTypeId") UUID leaveTypeId,
        @Param("year") int year
    );

    List<LeaveBalance> findByEmployeeIdAndYear(UUID employeeId, int year);

    @Query("""
        SELECT new com.hris.leave.dto.LeaveBalanceSummaryDto(
            lb.id,
            e.id,
            e.employeeCode,
            u.id,
            u.firstName,
            u.lastName,
            lb.leaveTypeId,
            lt.code,
            lt.name,
            lb.year,
            lb.totalDays,
            lb.usedDays,
            lb.pendingDays,
            lb.carryOverDays,
            lb.availableDays
        )
        FROM LeaveBalance lb
        JOIN Employee e ON lb.employeeId = e.id
        JOIN User u ON e.userId = u.id
        LEFT JOIN LeaveType lt ON lb.leaveTypeId = lt.id
        WHERE lb.year = :year
          AND (:employeeId IS NULL OR lb.employeeId = :employeeId)
          AND (
              :query IS NULL
              OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        ORDER BY u.lastName ASC, u.firstName ASC, lb.leaveTypeId ASC
        ORDER BY u.lastName ASC, u.firstName ASC, lb.leaveTypeId ASC
        """)
    Page<LeaveBalanceSummaryDto> searchSummariesForYear(
        @Param("year") int year,
        @Param("employeeId") UUID employeeId,
        @Param("query") String query,
        Pageable pageable
    );

    @Query("""
        SELECT new com.hris.leave.dto.LeaveBalanceSummaryDto(
            lb.id,
            e.id,
            e.employeeCode,
            u.id,
            u.firstName,
            u.lastName,
            lb.leaveTypeId,
            lt.code,
            lt.name,
            lb.year,
            lb.totalDays,
            lb.usedDays,
            lb.pendingDays,
            lb.carryOverDays,
            lb.availableDays
        )
        FROM LeaveBalance lb
        JOIN Employee e ON lb.employeeId = e.id
        JOIN User u ON e.userId = u.id
        LEFT JOIN LeaveType lt ON lb.leaveTypeId = lt.id
        WHERE lb.year = :year
          AND lb.employeeId IN :employeeIds
          AND (
              :query IS NULL
              OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        ORDER BY u.lastName ASC, u.firstName ASC, lb.leaveTypeId ASC
        """)
    Page<LeaveBalanceSummaryDto> searchSummariesForYearAndEmployeeIds(
        @Param("year") int year,
        @Param("employeeIds") List<UUID> employeeIds,
        @Param("query") String query,
        Pageable pageable
    );

    boolean existsByEmployeeId(UUID employeeId);
    void deleteByEmployeeId(UUID employeeId);
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.year = :year")
    List<LeaveBalance> findAllByYear(@Param("year") int year);
}
