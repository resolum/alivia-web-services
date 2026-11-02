package com.resolum.alivia.shared.infrastructure.persistence.jpa.schema;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Check constraint that limits a varchar column to the names of an enum.
 *
 * <p>PostgreSQL names a column check {@code <table>_<column>_check}, which is the name
 * Hibernate's schema export leaves on enum columns.</p>
 *
 * @param table  physical table name
 * @param column physical column name
 * @param values allowed enum names, in declaration order
 */
public record EnumCheckConstraint(String table, String column, List<String> values) {

    public String name() {
        return table + "_" + column + "_check";
    }

    /** Statements that replace the stored constraint with the current enum values. */
    public List<String> replaceStatements() {
        var allowed = values.stream().map(value -> "'" + value.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
        return List.of(
                "ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + name(),
                "ALTER TABLE " + table + " ADD CONSTRAINT " + name() + " CHECK (" + column + " IN (" + allowed + "))");
    }
}
