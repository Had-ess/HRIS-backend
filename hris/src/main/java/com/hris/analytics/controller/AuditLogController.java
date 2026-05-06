package com.hris.analytics.controller;

import com.hris.analytics.dto.AuditLogDto;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogQueryService;
import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'ADMINISTRATION')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogDto>>> search(
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        var page = auditLogQueryService.search(resource, action, actorId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(page)));
    }
}
