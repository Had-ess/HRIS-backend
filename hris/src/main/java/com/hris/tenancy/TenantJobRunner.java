package com.hris.tenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

/**
 * Per-tenant execution for scheduled jobs. Under RLS a job thread has no
 * tenant context and therefore reads zero rows — every tenant-scoped job must
 * iterate the active tenants explicitly. One tenant's failure never blocks
 * the others. The tenants table itself is global (not RLS'd), so listing it
 * needs no context.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantJobRunner {

    private final TenantRepository tenantRepository;
    private final TransactionTemplate transactionTemplate;

    public void forEachActiveTenant(String jobName, Consumer<Tenant> action) {
        for (Tenant tenant : tenantRepository.findByStatus(Tenant.STATUS_ACTIVE)) {
            try {
                TenantContext.runAs(tenant.getId(), () -> action.accept(tenant));
            } catch (RuntimeException ex) {
                log.error("{} failed for tenant {} — continuing with remaining tenants",
                    jobName, tenant.getSlug(), ex);
            }
        }
    }

    /**
     * Each tenant's work in its own fresh transaction. The transaction MUST
     * begin inside the tenant context: the RLS setting binds at connection
     * checkout, so a transaction opened before {@code runAs} would read
     * nothing.
     */
    public void forEachActiveTenantInTransaction(String jobName, Consumer<Tenant> action) {
        forEachActiveTenant(jobName, tenant ->
            transactionTemplate.executeWithoutResult(status -> action.accept(tenant)));
    }
}
