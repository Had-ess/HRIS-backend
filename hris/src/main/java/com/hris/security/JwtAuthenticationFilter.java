package com.hris.security;

import com.hris.auth.service.UserProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JIT provisioning filter. Runs after Spring Security's JWT validation.
 * Finds or creates local user from JWT claims, then injects local_user_id
 * as a claim so SecurityUtils.getCurrentUserId() works.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserProvisioningService userProvisioningService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt originalJwt = jwtAuth.getToken();

            try {
                UUID localUserId = userProvisioningService.resolveUserId(originalJwt);

                // Build a new JWT with local_user_id claim
                Map<String, Object> claims = new HashMap<>(originalJwt.getClaims());
                claims.put("local_user_id", localUserId.toString());

                Jwt enrichedJwt = Jwt.withTokenValue(originalJwt.getTokenValue())
                    .headers(h -> h.putAll(originalJwt.getHeaders()))
                    .claims(c -> c.putAll(claims))
                    .build();

                JwtAuthenticationToken enrichedAuth = new JwtAuthenticationToken(
                    enrichedJwt, jwtAuth.getAuthorities(), localUserId.toString());

                SecurityContextHolder.getContext().setAuthentication(enrichedAuth);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                log.warn("Rejecting request after JWT user provisioning failed", e);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication provisioning failed");
                return;
                // Don't block request — let security handle it downstream
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip public/health endpoints
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/");
    }
}
