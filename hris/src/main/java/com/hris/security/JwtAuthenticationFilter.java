package com.hris.security;

import com.hris.auth.repository.UserRoleRepository;
import com.hris.auth.service.UserProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final UserRoleRepository userRoleRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt originalJwt = jwtAuth.getToken();

            try {
                UUID localUserId = userProvisioningService.resolveUserId(originalJwt);
                Collection<GrantedAuthority> localAuthorities = userRoleRepository
                    .findEffectiveByUserId(localUserId, Instant.now())
                    .stream()
                    .map(userRole -> userRole.getRole())
                    .filter(role -> role != null && role.isActive())
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode().toUpperCase()))
                    .collect(Collectors.toSet());

                Map<String, Object> claims = new HashMap<>(originalJwt.getClaims());
                claims.put("local_user_id", localUserId.toString());

                Jwt enrichedJwt = Jwt.withTokenValue(originalJwt.getTokenValue())
                    .headers(headers -> headers.putAll(originalJwt.getHeaders()))
                    .claims(jwtClaims -> jwtClaims.putAll(claims))
                    .build();

                JwtAuthenticationToken enrichedAuth = new JwtAuthenticationToken(
                    enrichedJwt,
                    localAuthorities,
                    localUserId.toString()
                );

                SecurityContextHolder.getContext().setAuthentication(enrichedAuth);
            } catch (IllegalStateException e) {
                SecurityContextHolder.clearContext();
                log.warn("Rejecting request after JWT user provisioning conflict", e);
                response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
                return;
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                log.error("Rejecting request after unexpected JWT user provisioning failure", e);
                response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Authentication provisioning failed"
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/");
    }
}
