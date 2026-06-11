package com.hris.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Establishes {@link TenantContext} for API requests. Authenticated requests
 * carry the tenant in the JWT's {@code tid} claim; anonymous requests (the
 * account flows) fall back to request-level resolution (subdomain/parameter/
 * default). Registered explicitly on the API security chain — NOT a component,
 * Boot would register it globally.
 *
 * <p>Transition behavior: tokens issued before the tenancy cutover carry no
 * {@code tid} claim; those sessions map to the default tenant so the
 * single-tenant deployment keeps working through the rollout.
 */
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            TenantContext.set(resolveTenant(request));
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolveTenant(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String tid = jwtAuth.getToken().getClaimAsString("tid");
            return tid != null ? UUID.fromString(tid) : TenantContext.DEFAULT_TENANT_ID;
        }
        return tenantResolver.resolve(request);
    }
}
