package com.hris.organisation.controller;

import com.hris.common.ApiResponse;
import com.hris.organisation.dto.WorkScheduleDto;
import com.hris.organisation.service.WorkScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/work-schedules")
@RequiredArgsConstructor
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkScheduleDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(workScheduleService.getAll()));
    }
}
