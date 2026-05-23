package com.hris.settings.calendar.controller;

import com.hris.common.ApiResponse;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import com.hris.settings.calendar.dto.HrCalendarDto;
import com.hris.settings.calendar.dto.HrCalendarMutationDto;
import com.hris.settings.calendar.dto.HrHolidayDto;
import com.hris.settings.calendar.dto.HrHolidayMutationDto;
import com.hris.settings.calendar.service.HrCalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/hr-calendars")
@RequiredArgsConstructor
public class HrCalendarController {

    private final HrCalendarService hrCalendarService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<HrCalendarDto>>> getAll(Authentication authentication) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(hrCalendarService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HrCalendarDto>> getById(@PathVariable UUID id, Authentication authentication) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(hrCalendarService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HrCalendarDto>> create(
            @Valid @RequestBody HrCalendarMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "HR_CALENDAR", "MANAGE");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(hrCalendarService.create(dto, SecurityUtils.getCurrentUserId(authentication))));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<HrCalendarDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody HrCalendarMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "HR_CALENDAR", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(hrCalendarService.update(id, dto, SecurityUtils.getCurrentUserId(authentication))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "HR_CALENDAR", "MANAGE");
        hrCalendarService.deactivate(id, SecurityUtils.getCurrentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard-delete")
    public ResponseEntity<Void> hardDelete(@PathVariable UUID id, Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "HR_CALENDAR", "MANAGE");
        hrCalendarService.hardDelete(id, SecurityUtils.getCurrentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/holidays")
    public ResponseEntity<ApiResponse<List<HrHolidayDto>>> getHolidays(@PathVariable UUID id, Authentication authentication) {
        authorizeRead(authentication);
        return ResponseEntity.ok(ApiResponse.ok(hrCalendarService.getHolidays(id)));
    }

    @PostMapping("/{id}/holidays")
    public ResponseEntity<ApiResponse<HrHolidayDto>> createHoliday(
            @PathVariable UUID id,
            @Valid @RequestBody HrHolidayMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "HR_CALENDAR", "MANAGE");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(hrCalendarService.createHoliday(id, dto, SecurityUtils.getCurrentUserId(authentication))));
    }

    @PatchMapping("/{id}/holidays/{holidayId}")
    public ResponseEntity<ApiResponse<HrHolidayDto>> updateHoliday(
            @PathVariable UUID id,
            @PathVariable UUID holidayId,
            @Valid @RequestBody HrHolidayMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "HR_CALENDAR", "MANAGE");
        return ResponseEntity.ok(ApiResponse.ok(
            hrCalendarService.updateHoliday(id, holidayId, dto, SecurityUtils.getCurrentUserId(authentication))
        ));
    }

    @DeleteMapping("/{id}/holidays/{holidayId}")
    public ResponseEntity<Void> deleteHoliday(
            @PathVariable UUID id,
            @PathVariable UUID holidayId,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "HR_CALENDAR", "MANAGE");
        hrCalendarService.deleteHoliday(id, holidayId, SecurityUtils.getCurrentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    private void authorizeRead(Authentication authentication) {
        if (permissionAuthorizationService.hasPermission(authentication, "HR_CALENDAR", "READ")
            || permissionAuthorizationService.hasPermission(authentication, "HR_CALENDAR", "MANAGE")) {
            return;
        }
        permissionAuthorizationService.authorize(authentication, "HR_CALENDAR", "READ");
    }
}
