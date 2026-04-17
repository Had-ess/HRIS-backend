package com.hris.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/**
 * GAP-B-01 FIXED: No longer casts JWT sub directly to UUID.
 * The local user UUID is injected into the JWT authentication object
 * by the JwtAuthenticationFilter after JIT provisioning.
 *
 * The principal name is set to the local user UUID after provisioning.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Gets the local user UUID from the authentication.
     * After JwtAuthenticationFilter runs, the token carries a
     * "local_user_id" claim or we fall back to resolving via name.
     */
    public static UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("No authentication context");
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            // Check for local_user_id claim injected by JwtAuthenticationFilter
            String localId = jwt.getClaimAsString("local_user_id");
            if (localId != null) {
                return UUID.fromString(localId);
            }
        }

        // Fallback: principal name should be the local user UUID (set by filter)
        String name = authentication.getName();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "Cannot resolve local user ID from authentication. " +
                "Ensure JwtAuthenticationFilter is registered before this call.", e);
        }
    }

    /**
     * Gets the Keycloak subject (sub claim) from the JWT.
     */
    public static String getKeycloakId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        throw new IllegalStateException("Authentication is not JWT-based");
    }
}
