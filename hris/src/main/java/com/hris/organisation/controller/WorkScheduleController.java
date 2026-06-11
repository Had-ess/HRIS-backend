package com.hris.organisation.controller;

import com.hris.common.ApiResponse;
import com.hris.organisation.dto.WorkScheduleDto;
import com.hris.organisation.service.WorkScheduleService;
import com.hris.security.PermissionAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/work-schedules")
@RequiredArgsConstructor
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    /**
     * Work schedules are reference data for the employee create/edit forms,
     * so read access follows the employee permissions.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkScheduleDto>>> getAll(Authentication auth) {
        permissionAuthorizationService.authorizeAnyPermissionName(
            auth,
            "EMPLOYEE_READ",
            "EMPLOYEE_MANAGE"
        );
        return ResponseEntity.ok(ApiResponse.ok(workScheduleService.getAll()));
    }
}
