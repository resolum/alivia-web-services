package com.resolum.alivia.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What only a real PostgreSQL can prove about the schema: the migrations alone build a database that Hibernate accepts
 * ({@code validate}, as in production), every migration was applied, and the application wires up as a whole.
 */
class FlywaySchemaOnPostgresTest extends PostgresIntegrationTest {

    @Autowired
    private ApplicationContext context;

    private static int migrationFiles() throws Exception {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
        return resources.length;
    }

    @Test
    void theApplicationStartsOnTheSchemaThatTheMigrationsAloneBuild() {
        // Reaching this line means the context started: every bean has a unique name and Hibernate validated every table
        // and column of every entity against what Flyway created.
        assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
        // IAM and Assets each have a service called EdgeCredentialCommandServiceImpl: they only coexist by explicit names.
        assertTrue(context.containsBean("edgeCredentialCommandServiceImpl"));
        assertTrue(context.containsBean("assetsEdgeCredentialCommandService"));
        assertNotSame(context.getBean("edgeCredentialCommandServiceImpl"), context.getBean("assetsEdgeCredentialCommandService"));
    }

    @Test
    void everyMigrationWasAppliedOnceAndSucceeded() throws Exception {
        var versions = jdbc.queryForList(
                "select version from flyway_schema_history where success and version is not null order by installed_rank",
                String.class);

        assertEquals(migrationFiles(), versions.size(), versions.toString());
        var numeric = versions.stream().map(Integer::parseInt).toList();
        assertEquals(java.util.stream.IntStream.rangeClosed(1, numeric.size()).boxed().toList(), numeric,
                "versions are consecutive from V1, with no gaps");
        assertEquals(0, jdbc.queryForObject("select count(*) from flyway_schema_history where not success", Integer.class));
    }

    @Test
    void theTablesOfAssetsAndTheEdgeTransportExistUnderTheNamesHibernateExpects() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertTrue(tables.containsAll(List.of("devices", "edges", "device_assignments", "household_device_registries",
                "edge_credentials", "edge_sync_states")), tables.toString());
        // device_networks was replaced by the registry (V12).
        assertFalse(tables.contains("device_networks"));
        // Hibernate pluralizes table names: a singular name would pass here but not the validation above.
        assertFalse(tables.contains("edge_sync_state"));
    }

    @Test
    void theRulesTheDatabaseEnforcesForTheDevicesOfAHouseholdAreInPlace() {
        List<String> constraints = jdbc.queryForList("select conname from pg_constraint", String.class);
        List<String> indexes = jdbc.queryForList("select indexname from pg_indexes where schemaname = 'public'", String.class);

        assertTrue(constraints.containsAll(List.of(
                "uk_household_device_registries_household", "uk_household_device_registries_installation",
                "uk_household_device_registries_microphone_edge", "uk_household_device_registries_actuator_edge",
                "uk_edge_credentials_edge", "fk_device_assignments_registry", "fk_edge_sync_states_edge")), constraints.toString());
        assertTrue(indexes.contains("idx_device_assignments_registry_active"));
        assertTrue(indexes.contains("idx_edge_sync_states_installation"));
    }

    @Test
    void aDeviceCanHaveOnlyOneActiveAssignment() {
        // The partial unique index of V6 cannot be declared in the entity, so only the migration provides it.
        var indexDefinition = jdbc.queryForObject(
                "select indexdef from pg_indexes where tablename = 'device_assignments' and indexdef ilike '%unique%' "
                        + "and indexdef ilike '%where%' limit 1", String.class);

        assertNotNull(indexDefinition);
        assertTrue(indexDefinition.toLowerCase().contains("unassigned_at is null"), indexDefinition);
    }

    @Test
    void theEnumChecksAcceptTheValuesOfTheCurrentEnums() {
        // V13: the checks that the merged enums outgrew. The migrations alone must accept the new values.
        var checks = jdbc.queryForList(
                "select pg_get_constraintdef(oid) from pg_constraint where conname in "
                        + "('appointments_status_check', 'contract_versions_action_check', 'support_incidents_status_check')",
                String.class);

        assertEquals(3, checks.size());
        var joined = String.join(" ", checks);
        assertTrue(Arrays.stream(new String[]{"COMPLETED", "ACTIVATED", "IN_EVALUATION"}).allMatch(joined::contains), joined);
    }
}
