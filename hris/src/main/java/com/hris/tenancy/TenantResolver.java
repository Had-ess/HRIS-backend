package com.hris.tenancy;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Resolves the tenant for requests that carry no authenticated token yet:
 * the login page, the form-login POST, and the anonymous account flows.
 *
 * <p>Order: explicit {@code tenant} parameter (slug, dev & hidden form field)
 * → session attribute (remembered from the authorize request, so the slug
 * survives the redirect to {@code /login} when there is no subdomain)
 * → subdomain ({@code acme.hris.app} → {@code acme}) → default tenant. An
 * unknown slug resolves to the default tenant rather than erroring — the
 * login then simply fails, which does not leak which slugs exist.
 */
@Component
@RequiredArgsConstructor
public class TenantResolver {

    /** Session attribute carrying the tenant slug across the authorize → login redirect. */
    public static final String SESSION_ATTRIBUTE = "hris.tenant_slug";

    private static final Set<String> NON_TENANT_SUBDOMAINS = Set.of("www", "app", "api");

    private final TenantRepository tenantRepository;

    public UUID resolve(HttpServletRequest request) {
        UUID byParameter = resolveSlug(request.getParameter("tenant"));
        if (byParameter != null) {
            return byParameter;
        }

        var session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_ATTRIBUTE) instanceof String remembered) {
            UUID bySession = resolveSlug(remembered);
            if (bySession != null) {
                return bySession;
            }
        }

        String subdomain = subdomainOf(request.getServerName());
        if (subdomain != null) {
            UUID bySlug = lookup(subdomain);
            if (bySlug != null) {
                return bySlug;
            }
        }

        return TenantContext.DEFAULT_TENANT_ID;
    }

    /** Tenant id for a slug, or null when blank/unknown/inactive. */
    public UUID resolveSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return lookup(slug.trim().toLowerCase());
    }

    private UUID lookup(String slug) {
        return tenantRepository.findBySlug(slug)
            .filter(Tenant::isActive)
            .map(Tenant::getId)
            .orElse(null);
    }

    /** First DNS label when the host has 3+ labels and is not an IP/localhost. */
    private String subdomainOf(String host) {
        if (host == null || host.isBlank() || host.matches("[0-9.:\\[\\]]+")) {
            return null;
        }
        String[] labels = host.toLowerCase().split("\\.");
        if (labels.length < 3) {
            return null;
        }
        String first = labels[0];
        return NON_TENANT_SUBDOMAINS.contains(first) ? null : first;
    }
}
