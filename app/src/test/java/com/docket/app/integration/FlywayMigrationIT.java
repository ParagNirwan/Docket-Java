package com.docket.app.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Flyway database migrations.
 * Uses Testcontainers to spin up a real PostgreSQL database and verifies that
 * all migrations (V1 through V24) apply cleanly on an empty database.
 *
 * This test implements issue #111: Add PostgreSQL and Flyway integration test coverage
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class FlywayMigrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("docketdb")
            .withUsername("docket")
            .withPassword("docket_dev_password")
            .withReuse(false);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Flyway should manage schema - disable Hibernate auto-DDL
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    /**
     * Tests that all Flyway migrations apply cleanly on an empty database.
     * This is the primary CI gate - migrations must apply without errors.
     */
    @Test
    void allMigrationsApplyCleanlyOnEmptyDatabase() {
        // Apply all migrations
        flyway.migrate();

        // Verify all 24 migrations were applied
        var appliedMigrations = flyway.info().applied();
        assertThat(appliedMigrations)
                .as("All migrations should be applied")
                .hasSize(24);

        // Verify migration versions are sequential V1 through V24
        for (int i = 0; i < appliedMigrations.length; i++) {
            var migration = appliedMigrations[i];
            int expectedVersion = i + 1;
            assertThat(migration.getVersion().getVersion())
                    .as("Migration V%d should be applied", expectedVersion)
                    .isEqualTo(String.valueOf(expectedVersion));
        }
    }

    /**
     * Verifies that specific critical tables exist after all migrations.
     * This ensures the schema structure is correct.
     */
    @Test
    void criticalTablesExistAfterMigrations() {
        flyway.migrate();

        // Core tables from V1
        assertTableExists("users");
        assertTableExists("tenants");
        assertTableExists("tenant_membership");

        // Folders from V3
        assertTableExists("folders");

        // Documents from V4
        assertTableExists("documents");

        // Vector/chunks from V5
        assertTableExists("document_chunks");

        // Conversations from V6
        assertTableExists("conversations");
        assertTableExists("conversation_messages");

        // Audit from V10
        assertTableExists("audit_events");

        // Auth from V11, V12
        assertTableExists("refresh_tokens");
        assertTableExists("auth_rate_limits");

        // Account tokens from V13
        assertTableExists("account_tokens");
        assertTableExists("email_verification_tokens");

        // Email outbox from V9
        assertTableExists("email_outbox");

        // AI feedback from V20
        assertTableExists("ai_feedback");

        // AI summaries from V21
        assertTableExists("ai_document_summaries");

        // Phase 8 from V22
        assertTableExists("invites");
        assertTableExists("service_accounts");
        assertTableExists("api_keys");
        assertTableExists("webhook_endpoints");
        assertTableExists("webhook_deliveries");

        // Retention/Trash from V16
        assertTableExists("retention_policies");
        assertTableExists("retention_expiry");
        assertTableExists("trash");
        assertTableExists("notifications");

        // Legal holds from V17
        assertTableExists("legal_holds");
        assertTableExists("legal_hold_documents");

        // Favourites from V24
        assertTableExists("document_favourites");
    }

    /**
     * Tests that Flyway clean() works correctly (drops all objects).
     * This verifies rollback capability where applicable.
     */
    @Test
    void flywayCleanRemovesAllObjects() {
        // First apply all migrations
        flyway.migrate();
        assertThat(flyway.info().applied()).hasSize(24);

        // Clean the database
        flyway.clean();

        // Verify schema history is gone
        assertThat(flyway.info().applied()).isEmpty();

        // Verify tables are dropped (or we can re-migrate cleanly)
        flyway.migrate();
        assertThat(flyway.info().applied()).hasSize(24);
    }

    /**
     * Tests that migrations are idempotent - running migrate twice works.
     */
    @Test
    void migrationsAreIdempotent() {
        flyway.migrate();
        var firstRun = flyway.info().applied().length;

        // Running migrate again should be a no-op
        flyway.migrate();
        var secondRun = flyway.info().applied().length;

        assertThat(secondRun).isEqualTo(firstRun);
    }

    private void assertTableExists(String tableName) {
        try (var conn = dataSource.getConnection()) {
            var meta = conn.getMetaData();
            try (var rs = meta.getTables(null, "public", tableName, new String[]{"TABLE"})) {
                assertThat(rs.next())
                        .as("Table %s should exist", tableName)
                        .isTrue();
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to verify table " + tableName, e);
        }
    }
}