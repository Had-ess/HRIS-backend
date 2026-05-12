package com.hris.leave.dto;

import com.hris.leave.accrual.entity.AccrualRunStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveAccrualRunDto(
    UUID id,
    LocalDate runDate,
    Instant startedAt,
    Instant finishedAt,
    AccrualRunStatus status,
    int policiesProcessed,
    int employeesProcessed,
    int transactionsCreated,
    String errorMessage,
    String triggeredBy,
    UUID triggeredByUserId
) {}
