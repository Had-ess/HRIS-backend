package com.hris.tenancy;

import java.util.UUID;

/**
 * Per-thread tenant context. Set by {@link TenantContextFilter} for requests
 * and by {@code runAs} for jobs/provisioning. {@link TenantAwareDataSource}
 * translates it into the Postgres setting that RLS policies evaluate.
 *
 * <p>Absence of a context is a legal state (platform startup, Flyway,
 * maintenance jobs) — reads then return zero rows once RLS is active
 * (fail-closed), they never leak another tenant's data.
 */
public final class TenantContext {

    /** Tenant that all pre-tenancy data was backfilled into (V62). */
    public static final UUID DEFAULT_TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        if (tenantId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(tenantId);
        }
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs the action inside the given tenant, restoring the previous context after. */
    public static void runAs(UUID tenantId, Runnable action) {
        UUID previous = CURRENT.get();
        set(tenantId);
        try {
            action.run();
        } finally {
            set(previous);
        }
    }
}
