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
}
