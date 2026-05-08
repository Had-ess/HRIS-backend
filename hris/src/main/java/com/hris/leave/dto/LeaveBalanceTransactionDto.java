package com.hris.leave.dto;

import com.hris.leave.ledger.entity.LeaveBalanceTransactionSourceType;
import com.hris.leave.ledger.entity.LeaveBalanceTransactionType;

import java.time.Instant;
import java.util.UUID;

public record LeaveBalanceTransactionDto(
    UUID id,
    UUID employeeId,
    UUID leaveTypeId,
    LeaveBalanceTransactionType type,
    int amount,
    int balanceAfter,
    LeaveBalanceTransactionSourceType sourceType,
    UUID sourceId,
    String comment,
    UUID createdByUserId,
    Instant occurredAt
) {
}
