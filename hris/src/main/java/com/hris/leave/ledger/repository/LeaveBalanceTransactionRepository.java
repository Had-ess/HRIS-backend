package com.hris.leave.ledger.repository;

import com.hris.leave.ledger.entity.LeaveBalanceTransaction;
import com.hris.leave.ledger.entity.LeaveBalanceTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LeaveBalanceTransactionRepository extends JpaRepository<LeaveBalanceTransaction, UUID> {

    List<LeaveBalanceTransaction> findByEmployeeIdOrderByOccurredAtDesc(UUID employeeId);

    List<LeaveBalanceTransaction> findByEmployeeIdAndLeaveTypeIdOrderByOccurredAtDesc(UUID employeeId, UUID leaveTypeId);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM LeaveBalanceTransaction t
        WHERE t.employeeId = :employeeId
          AND t.leaveTypeId = :leaveTypeId
          AND t.type = :type
          AND t.occurredAt >= :fromInclusive
          AND t.occurredAt < :toExclusive
        """)
    int sumAmountByEmployeeLeaveTypeAndTypeBetween(
        @Param("employeeId") UUID employeeId,
        @Param("leaveTypeId") UUID leaveTypeId,
        @Param("type") LeaveBalanceTransactionType type,
        @Param("fromInclusive") Instant fromInclusive,
        @Param("toExclusive") Instant toExclusive
    );
}
