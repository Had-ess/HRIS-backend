package com.hris.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CoreDemoSeedMigrationTest {

    @Test
    void coreDemoSeedCoversIdentityAndOrgStructure() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V65__seed_core_demo_data.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration);

        assertThat(sql).contains(
            "users",
            "employees",
            "departments",
            "user_profile_assignments"
        );
        // Owned-auth + tenancy era: no Keycloak linkage, tenant-composite conflict targets.
        assertThat(sql).doesNotContain("INSERT INTO users (id, keycloak_id");
        assertThat(sql).contains("ON CONFLICT (tenant_id, email)");
    }
}
