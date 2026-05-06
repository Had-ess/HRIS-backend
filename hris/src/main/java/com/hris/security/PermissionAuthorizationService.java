package com.hris.security;

import com.hris.auth.entity.Permission;
import com.hris.auth.entity.RolePermission;
import com.hris.auth.repository.PermissionRepository;
import com.hris.auth.repository.RolePermissionRepository;
import com.hris.security.service.AccessScopeService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PermissionAuthorizationService {

    private final AccessScopeService accessScopeService;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    public PermissionAuthorizationService(
            AccessScopeService accessScopeService,
            RolePermissionRepository rolePermissionRepository,
            PermissionRepository permissionRepository) {
        this.accessScopeService = accessScopeService;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    public boolean hasPermission(Authentication authentication, String resource, String action) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        UUID userId;
        try {
            userId = SecurityUtils.getCurrentUserId(authentication);
        } catch (IllegalStateException ex) {
            return false;
        }

        List<UUID> roleIds = accessScopeService.getEffectiveRoles(userId).stream()
            .map(com.hris.auth.entity.UserRole::getRoleId)
            .toList();
        if (roleIds.isEmpty()) {
            return false;
        }

        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleIdIn(roleIds);
        if (rolePermissions.isEmpty()) {
            return false;
        }

        Set<UUID> permissionIds = rolePermissions.stream()
            .map(RolePermission::getPermissionId)
            .collect(Collectors.toSet());

        return permissionRepository.findByIdInAndIsActiveTrue(permissionIds).stream()
            .anyMatch(permission -> permissionMatches(permission, resource, action));
    }

    public boolean hasPermissionOrRole(
            Authentication authentication,
            String resource,
            String action,
            String... fallbackRoles) {
        return hasPermission(authentication, resource, action)
            || hasAnyRole(authentication, fallbackRoles);
    }

    public void authorize(
            Authentication authentication,
            String resource,
            String action,
            String... fallbackRoles) {
        if (!hasPermissionOrRole(authentication, resource, action, fallbackRoles)) {
            throw new AccessDeniedException("You do not have permission to perform this action");
        }
    }

    private boolean hasAnyRole(Authentication authentication, String... fallbackRoles) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Set<String> expectedAuthorities = List.of(fallbackRoles).stream()
            .map(role -> "ROLE_" + normalize(role))
            .collect(Collectors.toSet());

        try {
            UUID userId = SecurityUtils.getCurrentUserId(authentication);
            return accessScopeService.getEffectiveRoles(userId).stream()
                .map(com.hris.auth.entity.UserRole::getRole)
                .filter(role -> role != null && role.getCode() != null)
                .map(role -> normalize(role.getCode()))
                .anyMatch(roleCode -> expectedAuthorities.contains("ROLE_" + roleCode));
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    private boolean permissionMatches(Permission permission, String resource, String action) {
        return normalize(permission.getResource()).equals(normalize(resource))
            && normalize(permission.getAction()).equals(normalize(action));
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
