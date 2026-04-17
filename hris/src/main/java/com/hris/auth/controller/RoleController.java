package com.hris.auth.controller;

import com.hris.auth.entity.Role;
import com.hris.auth.service.RoleService;
import com.hris.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Role>> create(@RequestBody Role role) {
        Role saved = roleService.create(role);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(saved));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Role>> update(
            @PathVariable UUID id,
            @RequestBody Role role) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.update(id, role)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        roleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
