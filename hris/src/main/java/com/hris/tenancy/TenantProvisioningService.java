package com.hris.tenancy;

import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.entity.User;
import com.hris.auth.service.AccountProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Creates a tenant at runtime: clones the default tenant's configuration
 * catalog (profiles, grants, leave/admin config, calendars, settings) into the
 * new tenant, then provisions its first admin through the standard account
 * flow (activation email included).
 *
 * <p>The clone is two-phase because RLS binds a connection to one tenant:
 * a read transaction inside the default tenant's context loads the template,
 * a write transaction inside the new tenant's context inserts the copies.
 * Every cloned row gets a fresh id; FKs between cloned rows are remapped via
 * the pre-built id map, FKs to global catalogs (permissions, menu_items) are
 * kept as-is. tenant_id is never set explicitly — the column DEFAULT picks it
 * up from the session setting and the RLS WITH CHECK validates it.
 *
 * <p>The whole write phase (tenant row + baseline + admin user + activation
 * token) is ONE transaction: a failed provisioning leaves nothing behind.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    /** Subdomains that can never become tenants (see TenantResolver). */
    private static final Set<String> RESERVED_SLUGS = Set.of("default", "www", "app", "api", "admin", "platform", "auth");

    private static final String ADMIN_PROFILE_CODE = "ADMIN_CONSOLE";
    private static final String PLATFORM_PERMISSION = "PLATFORM_ADMIN";

    /** Configuration tables cloned from the default tenant, in FK dependency order. */
    private static final List<String> TEMPLATE_TABLES = List.of(
        "work_schedules",
        "job_titles",
        "performance_rating_scales",
        "performance_rating_levels",
        "performance_competencies",
        "performance_competency_job_families",
        "compensation_pay_grades",
        "compensation_merit_matrix",
        "compensation_bonus_plans",
        "public_holidays",
        "admin_request_types",
        "hr_calendars",
        "hr_holidays",
        "access_profiles",
        "profile_permissions",
        "profile_menu_access",
        "profile_assignment_rules",
        "validation_workflows",
        "leave_types",
        "leave_acquisition_policies",
        "leave_policies",
        "enterprise_settings"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final AccountProvisioningService accountProvisioningService;
    private final TransactionTemplate transactionTemplate;

    public record ProvisionResult(UUID tenantId, String slug, String name, String status, UUID adminUserId) {
    }

    public ProvisionResult create(String slug, String name, String adminEmail,
                                  String adminFirstName, String adminLastName, UUID actorId) {
        String normalizedSlug = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(normalizedSlug).matches()) {
            throw new IllegalArgumentException("Invalid tenant slug: lowercase letters, digits and hyphens only");
        }
        if (RESERVED_SLUGS.contains(normalizedSlug)) {
            throw new IllegalArgumentException("Tenant slug is reserved");
        }
        if (tenantRepository.findBySlug(normalizedSlug).isPresent()) {
            throw new IllegalStateException("Tenant slug already exists");
        }

        Map<String, List<Map<String, Object>>> template = readTemplate();
        Map<Object, UUID> idMap = buildIdMap(template);
        UUID adminProfileTemplateId = findTemplateProfileId(template);

        UUID tenantId = UUID.randomUUID();
        String tenantName = name.trim();
        AtomicReference<UUID> adminUserId = new AtomicReference<>();

        TenantContext.runAs(tenantId, () ->
            transactionTemplate.executeWithoutResult(tx -> {
                jdbcTemplate.update(
                    "INSERT INTO tenants (id, slug, name, status, created_at) VALUES (?, ?, ?, ?, NOW())",
                    tenantId, normalizedSlug, tenantName, Tenant.STATUS_ACTIVE);

                for (String table : TEMPLATE_TABLES) {
                    insertClones(table, template.get(table), idMap);
                }

                User admin = accountProvisioningService.provision(new AccountProvisioningRequest(
                    adminEmail, adminEmail, adminFirstName, adminLastName,
                    List.of(idMap.get(adminProfileTemplateId))
                ), actorId);
                adminUserId.set(admin.getId());
            }));

        log.info("Provisioned tenant {} ({}) with admin user {}", normalizedSlug, tenantId, adminUserId.get());
        return new ProvisionResult(tenantId, normalizedSlug, tenantName, Tenant.STATUS_ACTIVE, adminUserId.get());
    }

    /** Reads the default tenant's configuration rows (the template) in one read transaction. */
    private Map<String, List<Map<String, Object>>> readTemplate() {
        Map<String, List<Map<String, Object>>> template = new LinkedHashMap<>();
        TenantContext.runAs(TenantContext.DEFAULT_TENANT_ID, () ->
            transactionTemplate.executeWithoutResult(tx -> {
                for (String table : TEMPLATE_TABLES) {
                    template.put(table, jdbcTemplate.queryForList("SELECT * FROM " + table));
                }
                // The platform permission stays with the default tenant's operator
                // profile — new tenants must never receive it.
                UUID platformPermissionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM permissions WHERE name = ?", UUID.class, PLATFORM_PERMISSION);
                template.get("profile_permissions")
                    .removeIf(row -> platformPermissionId.equals(row.get("permission_id")));
            }));
        return template;
    }

    /** Pre-generates a fresh id for every template row so cross-table FKs can be remapped in any order. */
    private Map<Object, UUID> buildIdMap(Map<String, List<Map<String, Object>>> template) {
        Map<Object, UUID> idMap = new HashMap<>();
        for (List<Map<String, Object>> rows : template.values()) {
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                if (id != null) {
                    idMap.put(id, UUID.randomUUID());
                }
            }
        }
        return idMap;
    }

    private UUID findTemplateProfileId(Map<String, List<Map<String, Object>>> template) {
        return template.get("access_profiles").stream()
            .filter(row -> ADMIN_PROFILE_CODE.equals(row.get("code")))
            .map(row -> (UUID) row.get("id"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Default tenant has no " + ADMIN_PROFILE_CODE + " profile to clone"));
    }

    private void insertClones(String table, List<Map<String, Object>> rows, Map<Object, UUID> idMap) {
        for (Map<String, Object> row : rows) {
            List<String> columns = new ArrayList<>(row.size());
            List<Object> values = new ArrayList<>(row.size());
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String column = entry.getKey();
                Object value = entry.getValue();
                if ("tenant_id".equals(column)) {
                    continue; // column DEFAULT fills it from the session setting
                }
                if ("id".equals(column)) {
                    value = idMap.get(value);
                } else if ("granted_by_id".equals(column)) {
                    value = null; // template-tenant actors don't exist in the new tenant
                } else if (value != null && idMap.containsKey(value)) {
                    value = idMap.get(value); // FK to another cloned row
                }
                columns.add(column);
                values.add(value);
            }
            String sql = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES ("
                + columns.stream().map(c -> "?").collect(Collectors.joining(", ")) + ")";
            jdbcTemplate.update(sql, values.toArray());
        }
    }
}
