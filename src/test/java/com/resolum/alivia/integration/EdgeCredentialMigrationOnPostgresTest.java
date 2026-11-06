package com.resolum.alivia.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What V17 does to the credentials that already exist: a database at V16 with credentials, upgraded by Flyway as it
 * would be in production.
 */
class EdgeCredentialMigrationOnPostgresTest extends PostgresIntegrationTest {

    @Test
    void existingCredentialsKeepWorkingTakeTheirLastChangeAsIssueDateAndHaveNoIssuer() {
        var postgres = PostgresContainerSupport.container();
        jdbc.execute("drop database if exists upgrade_v17");
        jdbc.execute("create database upgrade_v17");
        var url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/upgrade_v17$1");

        Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target("16").load().migrate();
        var old = new JdbcTemplate(new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
        old.update("insert into edge_credentials (created_at, updated_at, edge_id, edge_kind, secret_hash, revoked_at) "
                + "values ('2026-06-01 08:00', '2026-09-15 10:30', 41, 'MICROPHONE', repeat('a', 64), null)");
        old.update("insert into edge_credentials (created_at, updated_at, edge_id, edge_kind, secret_hash, revoked_at) "
                + "values ('2026-06-01 08:00', '2026-10-01 09:00', 42, 'ACTUATOR', repeat('b', 64), '2026-10-01 09:00')");

        Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertEquals("2026-09-15 10:30:00", old.queryForObject(
                "select to_char(issued_at, 'YYYY-MM-DD HH24:MI:SS') from edge_credentials where edge_id = 41", String.class));
        assertNull(old.queryForObject("select issued_by from edge_credentials where edge_id = 41", Long.class));
        // A revoked one stays revoked, without an administrator or a reason it never had.
        assertNotNull(old.queryForObject("select revoked_at from edge_credentials where edge_id = 42", Object.class));
        assertNull(old.queryForObject("select revoked_by from edge_credentials where edge_id = 42", Long.class));
        assertNull(old.queryForObject("select revoked_reason from edge_credentials where edge_id = 42", String.class));
        // From now on the issue date is required.
        assertEquals("NO", old.queryForObject("select is_nullable from information_schema.columns "
                + "where table_name = 'edge_credentials' and column_name = 'issued_at'", String.class));
        assertEquals("YES", old.queryForObject("select is_nullable from information_schema.columns "
                + "where table_name = 'edge_credentials' and column_name = 'issued_by'", String.class));
        assertThrows(Exception.class, () -> old.update("insert into edge_credentials (created_at, updated_at, edge_id, "
                + "edge_kind, secret_hash) values (now(), now(), 43, 'ACTUATOR', repeat('c', 64))"));
        // The audit migration is the one that ran last of the two, whatever migrations come after it.
        assertEquals("17", old.queryForObject("select version from flyway_schema_history "
                + "where description = 'add edge credential audit' and success", String.class));
    }
}
