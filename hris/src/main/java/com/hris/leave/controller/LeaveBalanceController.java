package com.hris.leave.controller;

import com.hris.common.ApiResponse;
import com.hris.leave.dto.LeaveBalanceDto;
import com.hris.leave.service.LeaveBalanceService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
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
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getMyBalances(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(leaveBalanceService.getMyBalances(userId)));
    }

    @GetMapping("/employee/{id}")
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getForEmployee(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "LEAVE_BALANCE", "READ");
        UUID requesterId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(leaveBalanceService.getForEmployee(id, requesterId)));
    }
}
