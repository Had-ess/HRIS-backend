package com.hris.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/**
 * Resolves the local user id from the authentication. Tokens issued by the
 * embedded authorization server carry the local users.id both as {@code sub}
 * and as the {@code local_user_id} claim (kept for compatibility with code
 * written during the Keycloak era).
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("No authentication context");
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String localId = jwt.getClaimAsString("local_user_id");
            if (localId != null) {
                return UUID.fromString(localId);
            }
        }

        // Fallback: principal name is the subject, which is the local user id.
        String name = authentication.getName();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "Cannot resolve local user ID from authentication", e);
        }
    }
}
