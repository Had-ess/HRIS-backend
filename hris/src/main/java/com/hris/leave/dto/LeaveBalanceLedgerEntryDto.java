package com.hris.leave.dto;

import com.hris.leave.ledger.entity.LeaveBalanceTransactionSourceType;
import com.hris.leave.ledger.entity.LeaveBalanceTransactionType;

import java.time.Instant;
import java.util.UUID;

/**
 * A richer ledger entry exposed via GET /api/leave-balances/me/ledger.
 * Adds leaveTypeCode and leaveTypeName for UI display without extra lookups.
 */
public record LeaveBalanceLedgerEntryDto(
    UUID id,
    UUID leaveTypeId,
    String leaveTypeCode,
    String leaveTypeName,
    LeaveBalanceTransactionType type,
    int amount,
    int balanceAfter,
    LeaveBalanceTransactionSourceType sourceType,
    UUID sourceId,
    String comment,
    Instant occurredAt
) {}
