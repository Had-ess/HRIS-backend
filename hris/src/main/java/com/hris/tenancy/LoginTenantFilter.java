package com.hris.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establishes {@link TenantContext} on the authorization server's form-login
 * chain so the password check, the lockout listener, and (under RLS) the user
 * lookup all run inside the tenant the visitor is signing in to. Resolution:
 * hidden {@code tenant} form field / parameter → subdomain → default.
 */
@RequiredArgsConstructor
public class LoginTenantFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            rememberExplicitSlug(request);
            TenantContext.set(tenantResolver.resolve(request));
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * A valid {@code tenant} parameter (e.g. on {@code /oauth2/authorize}) is
     * remembered in the form-login session so the subsequent redirect to
     * {@code /login} — which carries no parameters — still renders and posts
     * against the tenant the SPA asked for. Subdomain deployments never need
     * this; it is what makes multi-tenant work on plain localhost.
     */
    private void rememberExplicitSlug(HttpServletRequest request) {
        String slug = request.getParameter("tenant");
        if (slug != null && tenantResolver.resolveSlug(slug) != null) {
            request.getSession().setAttribute(
                TenantResolver.SESSION_ATTRIBUTE, slug.trim().toLowerCase());
        }
    }
}
