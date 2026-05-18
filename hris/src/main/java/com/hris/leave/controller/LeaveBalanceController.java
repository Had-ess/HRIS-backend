package com.hris.leave.controller;

import com.hris.common.ApiResponse;
import com.hris.leave.dto.LeaveBalanceDto;
import com.hris.leave.dto.LeaveBalanceAdjustmentDto;
import com.hris.leave.dto.LeaveBalanceLedgerEntryDto;
import com.hris.leave.dto.LeaveBalanceProjectionDto;
import com.hris.leave.dto.LeaveBalanceSummaryDto;
import com.hris.leave.dto.LeaveBalanceTransactionDto;
import com.hris.leave.service.LeaveBalanceLedgerService;
import com.hris.leave.service.LeaveBalanceService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave-balances")
@RequiredArgsConstructor
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;
    private final LeaveBalanceLedgerService leaveBalanceLedgerService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getMyBalances(Authentication auth) {
        permissionAuthorizationService.authorizePermissionName(auth, "LEAVE_BALANCE_READ_OWN");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(leaveBalanceService.getMyBalances(userId)));
    }

    @GetMapping("/me/ledger")
    public ResponseEntity<ApiResponse<List<LeaveBalanceLedgerEntryDto>>> getMyLedger(Authentication auth) {
        permissionAuthorizationService.authorizePermissionName(auth, "LEAVE_BALANCE_READ_OWN");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        var employee = leaveBalanceService.resolveEmployeeByUserId(userId);
        return ResponseEntity.ok(ApiResponse.ok(leaveBalanceLedgerService.getLedgerForEmployee(employee.getId())));
    }

    @GetMapping("/me/projection")
    public ResponseEntity<ApiResponse<LeaveBalanceProjectionDto>> getMyProjection(Authentication auth) {
        permissionAuthorizationService.authorizePermissionName(auth, "LEAVE_BALANCE_READ_OWN");
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        var employee = leaveBalanceService.resolveEmployeeByUserId(userId);
        return ResponseEntity.ok(ApiResponse.ok(leaveBalanceLedgerService.getProjection(employee.getId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaveBalanceSummaryDto>>> getVisibleBalances(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer year,
            Pageable pageable,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "LEAVE_BALANCE_READ_OWN",
            "LEAVE_BALANCE_READ_SCOPED",
            "LEAVE_BALANCE_MANAGE"
        );
        UUID requesterId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
            leaveBalanceService.getVisibleBalances(requesterId, employeeId, query, year, pageable)
        ));
    }

    @GetMapping("/{employeeId}")
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getForEmployeeByPath(
            @PathVariable UUID employeeId,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "LEAVE_BALANCE_READ_SCOPED",
            "LEAVE_BALANCE_MANAGE"
        );
        UUID requesterId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(leaveBalanceService.getForEmployee(employeeId, requesterId)));
    }

    @GetMapping("/employee/{id}")
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getForEmployee(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "LEAVE_BALANCE_READ_SCOPED",
            "LEAVE_BALANCE_MANAGE"
        );
        UUID requesterId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(leaveBalanceService.getForEmployee(id, requesterId)));
    }

    @GetMapping("/{employeeId}/transactions")
    public ResponseEntity<ApiResponse<List<LeaveBalanceTransactionDto>>> getTransactions(
            @PathVariable UUID employeeId,
            Authentication authentication) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            authentication,
            "LEAVE_BALANCE_READ_SCOPED",
            "LEAVE_BALANCE_MANAGE"
        );
        UUID requesterId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(leaveBalanceService.getTransactions(employeeId, requesterId)));
    }

    @PostMapping("/{employeeId}/adjustments")
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> adjustBalance(
            @PathVariable UUID employeeId,
            @Valid @RequestBody LeaveBalanceAdjustmentDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorizePermissionName(authentication, "LEAVE_BALANCE_MANAGE");
        UUID requesterId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(leaveBalanceService.adjustBalance(employeeId, dto, requesterId)));
    }
}

