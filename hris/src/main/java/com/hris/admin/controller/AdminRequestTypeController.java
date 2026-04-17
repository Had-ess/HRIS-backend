package com.hris.admin.controller;

import com.hris.admin.dto.AdminRequestTypeDto;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.mapper.AdminRequestMapper;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.common.ApiResponse;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/admin-request-types") @RequiredArgsConstructor
public class AdminRequestTypeController {
    private final AdminRequestTypeRepository repository;
    private final AdminRequestMapper mapper;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminRequestTypeDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(repository.findAll().stream().map(mapper::toTypeDto).collect(Collectors.toList())));
    }
    @PostMapping @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<AdminRequestTypeDto>> create(@RequestBody AdminRequestType type) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(mapper.toTypeDto(repository.save(type))));
    }
    @PatchMapping("/{id}") @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<AdminRequestTypeDto>> update(@PathVariable UUID id, @RequestBody AdminRequestType type) {
        AdminRequestType existing = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Type not found"));
        existing.setName(type.getName()); existing.setCode(type.getCode()); existing.setActive(type.isActive());
        return ResponseEntity.ok(ApiResponse.ok(mapper.toTypeDto(repository.save(existing))));
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        AdminRequestType existing = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Type not found"));
        existing.setActive(false); repository.save(existing);
        return ResponseEntity.noContent().build();
    }
}
