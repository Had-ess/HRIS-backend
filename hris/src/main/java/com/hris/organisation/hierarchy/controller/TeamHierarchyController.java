package com.hris.organisation.hierarchy.controller;

import com.hris.common.ApiResponse;
import com.hris.organisation.hierarchy.dto.TeamHierarchyMutationDto;
import com.hris.organisation.hierarchy.dto.TeamHierarchyNodeDto;
import com.hris.organisation.hierarchy.service.TeamHierarchyService;
import com.hris.security.PermissionAuthorizationService;
import com.hris.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/team-hierarchy")
@RequiredArgsConstructor
public class TeamHierarchyController {

    private final TeamHierarchyService teamHierarchyService;
    private final PermissionAuthorizationService permissionAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamHierarchyNodeDto>>> getHierarchy(
            @RequestParam UUID teamId,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "TEAM", "READ");
        return ResponseEntity.ok(ApiResponse.ok(teamHierarchyService.getHierarchy(teamId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamHierarchyNodeDto>> create(
            @Valid @RequestBody TeamHierarchyMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "TEAM_HIERARCHY", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(teamHierarchyService.create(dto, actorId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamHierarchyNodeDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody TeamHierarchyMutationDto dto,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "TEAM_HIERARCHY", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(teamHierarchyService.update(id, dto, actorId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> endRelation(
            @PathVariable UUID id,
            Authentication authentication) {
        permissionAuthorizationService.authorize(authentication, "TEAM_HIERARCHY", "MANAGE");
        UUID actorId = SecurityUtils.getCurrentUserId(authentication);
        teamHierarchyService.endRelation(id, actorId);
        return ResponseEntity.noContent().build();
    }
}
