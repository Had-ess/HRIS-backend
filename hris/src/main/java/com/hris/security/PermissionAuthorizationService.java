package com.hris.security;

import com.hris.access.service.AccessResolutionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PermissionAuthorizationService {

    private final AccessResolutionService accessResolutionService;

    public PermissionAuthorizationService(AccessResolutionService accessResolutionService) {
        this.accessResolutionService = accessResolutionService;
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

        return accessResolutionService.hasPermission(userId, resource, action);
    }

    public void authorize(Authentication authentication, String resource, String action) {
        if (!hasPermission(authentication, resource, action)) {
            throw new AccessDeniedException("You do not have permission to perform this action");
        }
    }

    public boolean hasPermissionName(Authentication authentication, String permissionName) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        UUID userId;
        try {
            userId = SecurityUtils.getCurrentUserId(authentication);
        } catch (IllegalStateException ex) {
            return false;
        }

        return accessResolutionService.hasPermissionName(userId, permissionName);
    }

    public boolean hasAnyPermissionName(Authentication authentication, String... permissionNames) {
        for (String permissionName : permissionNames) {
            if (hasPermissionName(authentication, permissionName)) {
                return true;
            }
        }
        return false;
    }

    public void authorizePermissionName(Authentication authentication, String permissionName) {
        if (!hasPermissionName(authentication, permissionName)) {
            throw new AccessDeniedException("You do not have permission to perform this action");
        }
    }

    public void authorizeAnyPermissionName(Authentication authentication, String... permissionNames) {
        if (!hasAnyPermissionName(authentication, permissionNames)) {
            throw new AccessDeniedException("You do not have permission to perform this action");
        }
    }
}
