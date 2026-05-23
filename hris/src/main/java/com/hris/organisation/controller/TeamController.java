package com.hris.organisation.controller;

import com.hris.common.ApiResponse;
import com.hris.common.PageResponse;
import com.hris.organisation.dto.TeamCreateDto;
import com.hris.organisation.dto.TeamDto;
import com.hris.organisation.dto.TeamUpdateDto;
import com.hris.organisation.service.TeamService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TeamDto>>> getAll(
            Pageable pageable,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "TEAM", "READ");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(teamService.getAll(actorId, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamDto>> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "TEAM", "READ");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(teamService.getById(id, actorId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamDto>> create(
            @Valid @RequestBody TeamCreateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "TEAM", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(teamService.create(dto, actorId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody TeamUpdateDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "TEAM", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(teamService.update(id, dto, actorId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "TEAM", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        teamService.deactivate(id, actorId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard-delete")
    public ResponseEntity<Void> hardDelete(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "TEAM", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        teamService.hardDelete(id, actorId);
        return ResponseEntity.noContent().build();
    }
}
