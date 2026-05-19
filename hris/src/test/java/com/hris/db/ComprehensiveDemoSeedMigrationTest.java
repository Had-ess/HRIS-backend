package com.hris.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ComprehensiveDemoSeedMigrationTest {

    @Test
    void comprehensiveDemoSeedCoversCoreHrisDomains() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V22__comprehensive_demo_seed.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration);

        assertThat(sql).contains(
            "employees",
            "departments",
            "projects",
            "leave_requests",
            "approval_steps",
            "admin_requests",
            "notifications",
            "audit_logs",
            "hr_holidays",
            "analytics_headcount_metrics_snapshots",
            "user_profile_assignments"
        );
        assertThat(sql).doesNotContain("timesheet", "imputation", "client_id");
    }

    @Test
    void repairMigrationNormalizesLegacyProjectAssignmentRoles() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V26__repair_legacy_dashboard_and_project_assignment_data.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration);

        assertThat(sql).contains("UPDATE project_assignments")
            .contains("'LEAD'")
            .contains("'MANAGER'");
    }
}
