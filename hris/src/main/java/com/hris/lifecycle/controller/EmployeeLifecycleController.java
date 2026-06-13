package com.hris.lifecycle.controller;

import com.hris.common.ApiResponse;
import com.hris.lifecycle.dto.LifecycleDtos.ContractDto;
import com.hris.lifecycle.dto.LifecycleDtos.CreateContractRequest;
import com.hris.lifecycle.dto.LifecycleDtos.LifecycleStateDto;
import com.hris.lifecycle.dto.LifecycleDtos.ReactivateRequest;
import com.hris.lifecycle.dto.LifecycleDtos.TerminateRequest;
import com.hris.lifecycle.dto.LifecycleDtos.TransferRequest;
import com.hris.lifecycle.service.EmployeeContractService;
import com.hris.lifecycle.service.EmployeeLifecycleService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees/{employeeId}")
@RequiredArgsConstructor
public class EmployeeLifecycleController {

    private final EmployeeContractService contractService;
    private final EmployeeLifecycleService lifecycleService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping("/contracts")
    public ResponseEntity<ApiResponse<List<ContractDto>>> listContracts(
            @PathVariable UUID employeeId, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "READ");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(contractService.listContracts(employeeId, actorId)));
    }

    @PostMapping("/contracts")
    public ResponseEntity<ApiResponse<ContractDto>> createContract(
            @PathVariable UUID employeeId,
            @Valid @RequestBody CreateContractRequest request,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(contractService.createContract(employeeId, request, actorId)));
    }

    @GetMapping("/lifecycle")
    public ResponseEntity<ApiResponse<LifecycleStateDto>> lifecycle(
            @PathVariable UUID employeeId, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "READ");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(lifecycleService.getLifecycleState(employeeId, actorId)));
    }

    @PostMapping("/terminate")
    public ResponseEntity<ApiResponse<LifecycleStateDto>> terminate(
            @PathVariable UUID employeeId,
            @Valid @RequestBody TerminateRequest request,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(lifecycleService.terminate(employeeId, request, actorId)));
    }

    @PostMapping("/cancel-termination")
    public ResponseEntity<ApiResponse<LifecycleStateDto>> cancelTermination(
            @PathVariable UUID employeeId, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(lifecycleService.cancelScheduledTermination(employeeId, actorId)));
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<LifecycleStateDto>> transfer(
            @PathVariable UUID employeeId,
            @Valid @RequestBody TransferRequest request,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(lifecycleService.transfer(employeeId, request, actorId)));
    }

    @PostMapping("/cancel-transfer")
    public ResponseEntity<ApiResponse<LifecycleStateDto>> cancelTransfer(
            @PathVariable UUID employeeId, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(lifecycleService.cancelScheduledTransfer(employeeId, actorId)));
    }

    @PostMapping("/reactivate")
    public ResponseEntity<ApiResponse<LifecycleStateDto>> reactivate(
            @PathVariable UUID employeeId,
            @Valid @RequestBody ReactivateRequest request,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "EMPLOYEE", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(lifecycleService.reactivate(employeeId, request, actorId)));
    }
}
