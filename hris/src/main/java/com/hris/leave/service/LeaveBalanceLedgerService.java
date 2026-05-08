package com.hris.leave.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InsufficientLeaveBalanceException;
import com.hris.leave.dto.LeaveBalanceAdjustmentDto;
import com.hris.leave.dto.LeaveBalanceTransactionDto;
import com.hris.leave.entity.LeaveBalance;
import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.entity.LeaveType;
import com.hris.leave.ledger.entity.LeaveBalanceTransaction;
import com.hris.leave.ledger.entity.LeaveBalanceTransactionSourceType;
import com.hris.leave.ledger.entity.LeaveBalanceTransactionType;
import com.hris.leave.ledger.repository.LeaveBalanceTransactionRepository;
import com.hris.leave.repository.LeaveBalanceRepository;
import com.hris.leave.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveBalanceLedgerService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceTransactionRepository transactionRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveAcquisitionPolicyService leaveAcquisitionPolicyService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<LeaveBalanceTransactionDto> getTransactions(UUID employeeId) {
        return transactionRepository.findByEmployeeIdOrderByOccurredAtDesc(employeeId).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public int getAvailableBalance(UUID employeeId, UUID leaveTypeId, int year) {
        return leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveTypeId, year)
            .map(LeaveBalance::getAvailableDays)
            .orElse(0);
    }

    @Transactional
    public LeaveBalance adjustBalance(UUID employeeId, LeaveBalanceAdjustmentDto dto, UUID actorId) {
        LeaveType leaveType = leaveTypeRepository.findById(dto.leaveTypeId())
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        int year = LocalDate.now().getYear();
        LeaveBalance balance = getOrCreateBalanceForUpdate(employeeId, leaveType.getId(), year);
        balance.adjustTotalDays(dto.amount());
        leaveBalanceRepository.save(balance);
        recordTransaction(
            balance,
            LeaveBalanceTransactionType.MANUAL_ADJUSTMENT,
            dto.amount(),
            LeaveBalanceTransactionSourceType.MANUAL_ADJUSTMENT,
            null,
            dto.comment(),
            actorId,
            Instant.now()
        );
        auditLogService.log(actorId, AuditAction.UPDATE, "leave_balance", balance.getId(), null, balance);
        return balance;
    }

    @Transactional
    public LeaveBalance reserveForLeaveRequest(Employee employee, LeaveType leaveType, LeaveRequest request, int amount, UUID actorId) {
        if (!leaveType.isBalanceTracked()) {
            return null;
        }
        LeaveBalance balance = getOrCreateBalanceForUpdate(employee.getId(), leaveType.getId(), request.getStartDate().getYear());
        boolean negativeAllowed = isNegativeBalanceAllowed(leaveType.getId(), request.getStartDate());
        if (!negativeAllowed && balance.getAvailableDays() < amount) {
            throw new InsufficientLeaveBalanceException(
                String.format("Insufficient balance. Available: %d, Requested: %d", balance.getAvailableDays(), amount)
            );
        }
        balance.deductDays(amount);
        leaveBalanceRepository.save(balance);
        recordTransaction(
            balance,
            LeaveBalanceTransactionType.REQUEST_RESERVATION,
            amount,
            LeaveBalanceTransactionSourceType.LEAVE_REQUEST,
            request.getId(),
            "Leave request reservation",
            actorId,
            Instant.now()
        );
        return balance;
    }

    @Transactional
    public void confirmApprovedLeaveRequest(LeaveRequest request, UUID actorId) {
        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        if (!leaveType.isBalanceTracked()) {
            return;
        }
        LeaveBalance balance = getOrCreateBalanceForUpdate(
            request.getEmployeeId(),
            request.getLeaveTypeId(),
            request.getStartDate().getYear()
        );
        balance.confirmUsage(request.getWorkingDays());
        leaveBalanceRepository.save(balance);
        recordTransaction(
            balance,
            LeaveBalanceTransactionType.REQUEST_APPROVAL_CONFIRMATION,
            request.getWorkingDays(),
            LeaveBalanceTransactionSourceType.LEAVE_REQUEST,
            request.getId(),
            "Leave request approved",
            actorId,
            Instant.now()
        );
    }

    @Transactional
    public void releaseRejectedLeaveRequest(LeaveRequest request, UUID actorId) {
        releasePendingRequest(request, LeaveBalanceTransactionType.REQUEST_REJECTION_RELEASE, "Leave request rejected", actorId);
    }

    @Transactional
    public void releaseCancelledLeaveRequest(LeaveRequest request, UUID actorId) {
        releasePendingRequest(request, LeaveBalanceTransactionType.CANCELLATION_RELEASE, "Leave request cancelled", actorId);
    }

    @Transactional
    public LeaveBalance applyAccrual(
            Employee employee,
            LeaveType leaveType,
            int year,
            int amount,
            UUID sourceId,
            UUID actorId,
            String comment,
            Instant occurredAt) {
        LeaveBalance balance = getOrCreateBalanceForUpdate(employee.getId(), leaveType.getId(), year);
        balance.adjustTotalDays(amount);
        leaveBalanceRepository.save(balance);
        recordTransaction(
            balance,
            LeaveBalanceTransactionType.ACCRUAL,
            amount,
            LeaveBalanceTransactionSourceType.ACQUISITION_POLICY,
            sourceId,
            comment,
            actorId,
            occurredAt
        );
        return balance;
    }

    private void releasePendingRequest(LeaveRequest request, LeaveBalanceTransactionType type, String comment, UUID actorId) {
        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
        if (!leaveType.isBalanceTracked()) {
            return;
        }
        LeaveBalance balance = getOrCreateBalanceForUpdate(
            request.getEmployeeId(),
            request.getLeaveTypeId(),
            request.getStartDate().getYear()
        );
        balance.restoreDays(request.getWorkingDays());
        leaveBalanceRepository.save(balance);
        recordTransaction(
            balance,
            type,
            request.getWorkingDays(),
            LeaveBalanceTransactionSourceType.LEAVE_REQUEST,
            request.getId(),
            comment,
            actorId,
            Instant.now()
        );
    }

    private LeaveBalanceTransaction recordTransaction(
            LeaveBalance balance,
            LeaveBalanceTransactionType type,
            int amount,
            LeaveBalanceTransactionSourceType sourceType,
            UUID sourceId,
            String comment,
            UUID actorId,
            Instant occurredAt) {
        return transactionRepository.save(LeaveBalanceTransaction.builder()
            .employeeId(balance.getEmployeeId())
            .leaveTypeId(balance.getLeaveTypeId())
            .type(type)
            .amount(amount)
            .balanceAfter(balance.getAvailableDays())
            .sourceType(sourceType)
            .sourceId(sourceId)
            .comment(comment)
            .createdByUserId(actorId)
            .occurredAt(occurredAt)
            .build());
    }

    private LeaveBalance getOrCreateBalanceForUpdate(UUID employeeId, UUID leaveTypeId, int year) {
        return leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYearForUpdate(employeeId, leaveTypeId, year)
            .orElseGet(() -> leaveBalanceRepository.save(LeaveBalance.builder()
                .employeeId(employeeId)
                .leaveTypeId(leaveTypeId)
                .year(year)
                .totalDays(0)
                .usedDays(0)
                .pendingDays(0)
                .carryOverDays(0)
                .build()));
    }

    private boolean isNegativeBalanceAllowed(UUID leaveTypeId, LocalDate date) {
        var policy = leaveAcquisitionPolicyService.resolveEffectivePolicy(leaveTypeId, date);
        return policy != null && policy.isNegativeBalanceAllowed();
    }

    private LeaveBalanceTransactionDto toDto(LeaveBalanceTransaction transaction) {
        return new LeaveBalanceTransactionDto(
            transaction.getId(),
            transaction.getEmployeeId(),
            transaction.getLeaveTypeId(),
            transaction.getType(),
            transaction.getAmount(),
            transaction.getBalanceAfter(),
            transaction.getSourceType(),
            transaction.getSourceId(),
            transaction.getComment(),
            transaction.getCreatedByUserId(),
            transaction.getOccurredAt()
        );
    }
}
