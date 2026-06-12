package com.hris.timesheet.controller;

import com.hris.common.ApiResponse;
import com.hris.security.SecurityUtils;
import com.hris.timesheet.dto.TimesheetDtos.CreateTimesheetRequest;
import com.hris.timesheet.dto.TimesheetDtos.RejectRequest;
import com.hris.timesheet.dto.TimesheetDtos.ReplaceEntriesRequest;
import com.hris.timesheet.dto.TimesheetDtos.SummaryDto;
import com.hris.timesheet.dto.TimesheetDtos.TimesheetDto;
import com.hris.timesheet.service.TimesheetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/timesheets")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TimesheetController {

    private final TimesheetService timesheetService;

    @GetMapping("/my")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'TIMESHEET', 'MANAGE_OWN')")
    public ResponseEntity<ApiResponse<List<TimesheetDto>>> my(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(timesheetService.myTimesheets(userId, from, to)));
    }

    @PostMapping
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'TIMESHEET', 'MANAGE_OWN')")
    public ResponseEntity<ApiResponse<TimesheetDto>> create(
            Authentication auth, @Valid @RequestBody CreateTimesheetRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(timesheetService.create(userId, request.periodStart())));
    }

    @GetMapping("/pending")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'TIMESHEET', 'APPROVE')")
    public ResponseEntity<ApiResponse<List<TimesheetDto>>> pending(Authentication auth) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(timesheetService.pendingForApprover(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TimesheetDto>> get(Authentication auth, @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(timesheetService.get(userId, id)));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponse<SummaryDto>> summary(Authentication auth, @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(timesheetService.summary(userId, id)));
    }

    @PutMapping("/{id}/entries")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'TIMESHEET', 'MANAGE_OWN')")
    public ResponseEntity<ApiResponse<TimesheetDto>> replaceEntries(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody ReplaceEntriesRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(timesheetService.replaceEntries(userId, id, request.entries())));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'TIMESHEET', 'MANAGE_OWN')")
    public ResponseEntity<ApiResponse<TimesheetDto>> submit(Authentication auth, @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(timesheetService.submit(userId, id)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'TIMESHEET', 'APPROVE')")
    public ResponseEntity<ApiResponse<TimesheetDto>> approve(Authentication auth, @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(timesheetService.approve(userId, id)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'TIMESHEET', 'APPROVE')")
    public ResponseEntity<ApiResponse<TimesheetDto>> reject(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId(auth);
        return ResponseEntity.ok(ApiResponse.ok(timesheetService.reject(userId, id, request.reason())));
    }
}
