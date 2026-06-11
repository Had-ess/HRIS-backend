package com.hris.tenancy;

import java.util.Locale;
import java.util.UUID;

/**
 * The canonical authenticated principal is the composite
 * {@code "<tenantId>:<email>"}. It is what lands in
 * {@code oauth2_authorization.principal_name} and
 * {@code spring_session.principal_name}, keeping both tables tenant-correct
 * without schema changes, and making session revocation precise when the same
 * email exists in two tenants. The frontend never sees the composite —
 * {@code TokenClaimsCustomizer} emits clean {@code tid} and bare {@code email}
 * claims.
 */
public record TenantPrincipal(UUID tenantId, String email) {

    public String format() {
        return tenantId + ":" + email;
    }

    /**
     * Parses a composite principal. Plain (non-composite) values — e.g. the
     * raw email typed into the login form, seen by failure listeners — map to
     * the default tenant.
     */
    public static TenantPrincipal parse(String principal) {
        String value = principal == null ? "" : principal.trim();
        int separator = value.indexOf(':');
        if (separator > 0) {
            try {
                UUID tenantId = UUID.fromString(value.substring(0, separator));
                return new TenantPrincipal(tenantId, normalizeEmail(value.substring(separator + 1)));
            } catch (IllegalArgumentException ignored) {
                // not a composite; fall through
            }
        }
        return new TenantPrincipal(TenantContext.DEFAULT_TENANT_ID, normalizeEmail(value));
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
