package com.resolum.alivia.shared;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the conventions of the Flyway migrations in {@code db/migration}.
 *
 * <p>Flyway fails at startup when two files share a version, when a file name is not
 * recognised or when an applied migration is edited (checksum mismatch). This test catches
 * the naming and ordering mistakes earlier, without a database: the migrations are
 * PostgreSQL DDL, so they cannot run on the H2 database of the {@code test} profile.</p>
 */
class FlywayMigrationTest {

    private static final Pattern FILE_NAME =
            Pattern.compile("^V(\\d+(?:\\.\\d+)*)__([a-z0-9]+(?:_[a-z0-9]+)*)\\.sql$");

    @Test
    void namesEveryMigrationWithAVersionAndADescription() {
        assertThat(migrationFiles())
                .as("files in db/migration")
                .isNotEmpty()
                .allSatisfy(file -> assertThat(FILE_NAME.matcher(file.getFileName().toString()).matches())
                        .as("%s must match V<version>__<lower_snake_case_description>.sql", file.getFileName())
                        .isTrue());
    }

    @Test
    void assignsAUniqueVersionToEachMigration() {
        List<String> versions = migrationFiles().stream()
                .map(FlywayMigrationTest::versionOf)
                .toList();

        assertThat(versions).doesNotHaveDuplicates();
    }

    @Test
    void startsFromANonEmptyV1() {
        Path v1 = migrationFiles().stream()
                .filter(file -> "1".equals(versionOf(file)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("V1 is missing: Flyway needs a starting version"));

        assertThat(read(v1)).as("%s must define the schema", v1.getFileName())
                .contains("CREATE TABLE");
    }

    @Test
    void doesNotContainPgDumpArtifacts() {
        // pg_dump adds the backslash-prefixed restrict/unrestrict markers (psql meta-commands)
        // and CREATE SCHEMA public; Flyway cannot parse the former and the latter fails when
        // the schema already exists.
        migrationFiles().forEach(file -> {
            List<String> lines = read(file).lines().toList();
            assertThat(lines)
                    .as("%s must not contain psql meta-commands", file.getFileName())
                    .noneMatch(line -> line.startsWith("\\"));
            assertThat(lines)
                    .as("%s must not create the schema: Flyway manages it", file.getFileName())
                    .noneMatch(line -> line.stripLeading().toUpperCase().startsWith("CREATE SCHEMA"));
        });
    }

    private static List<Path> migrationFiles() {
        try (Stream<Path> files = Files.list(migrationDirectory())) {
            List<Path> migrations = new ArrayList<>(files
                    .filter(file -> file.getFileName().toString().endsWith(".sql"))
                    .toList());
            migrations.sort(Comparator.comparing(Path::getFileName));
            return migrations;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path migrationDirectory() {
        try {
            return new ClassPathResource("db/migration").getFile().toPath();
        } catch (IOException exception) {
            throw new UncheckedIOException("db/migration is not readable from the classpath", exception);
        }
    }

    private static String versionOf(Path file) {
        Matcher matcher = FILE_NAME.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            throw new AssertionError("Unexpected migration file name: " + file.getFileName());
        }
        return matcher.group(1);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
