package com.resolum.alivia.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Flyway is wired to the application context and that the baseline
 * migration is recorded when the application starts. It runs on the in-memory H2
 * database of the {@code test} profile, so no PostgreSQL server is required.
 */
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void recordsTheBaselineMigrationOnStartup() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

        // Flyway 12 also records the creation of the history table itself (version null).
        var applied = history.stream()
                .filter(row -> "1".equals(row.get("version")))
                .toList();

        assertThat(applied).hasSize(1);
        assertThat(applied.getFirst()).containsEntry("success", true);
    }
}
