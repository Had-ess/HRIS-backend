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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'AUDIT_LOG', 'READ')")
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

    @GetMapping("/export")
    @PreAuthorize("@permissionAuthorizationService.hasPermission(authentication, 'AUDIT_LOG', 'READ')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String resource
    ) {
        List<AuditLogDto> logs = auditLogQueryService.findForExport(resource, action, from, to);

        StringBuilder csv = new StringBuilder();
        csv.append("﻿"); // UTF-8 BOM for Excel compatibility
        csv.append("Date,Acteur,Action,Ressource,Détails\n");
        for (AuditLogDto log : logs) {
            csv.append(String.join(",",
                escapeCsv(log.timestamp() != null ? log.timestamp().toString() : ""),
                escapeCsv(log.actorName()),
                escapeCsv(log.action() != null ? log.action().name() : ""),
                escapeCsv(log.resource()),
                escapeCsv(log.newState())
            )).append("\n");
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "audit-log-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
