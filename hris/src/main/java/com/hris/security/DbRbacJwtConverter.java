package com.hris.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Converts a validated JWT into a Spring Security authentication token.
 *
 * <p>Authorities are intentionally left EMPTY. This system does not put
 * roles in tokens or use hasRole/hasAuthority for access control decisions.
 * Authorization is enforced server-side via a database-driven RBAC model:
 *
 * <pre>
 *   JWT subject (= local users.id)
 *     → UserProfileAssignment links User to AccessProfile(s)
 *     → ProfilePermission maps AccessProfile to Permission entries
 *     → PermissionAuthorizationService.authorize() checks the resolved set
 * </pre>
 *
 * <p>Adding role claims to authorities here would create a parallel
 * authorization path that is never consulted, which would be misleading.
 * If Spring Security method-level annotations ({@code @PreAuthorize}, {@code @Secured})
 * are introduced in future, populate authorities here from a token claim.
 */
@Component
public class DbRbacJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, Collections.emptyList());
    }
}
