package com.resolum.alivia.shared;


import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.strategy.SnakeCaseWithPluralizedTablePhysicalNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SnakeCaseWithPluralizedTablePhysicalNamingStrategyTest {

    private final SnakeCaseWithPluralizedTablePhysicalNamingStrategy strategy =
            new SnakeCaseWithPluralizedTablePhysicalNamingStrategy();

    private static Identifier id(String text) {
        return Identifier.toIdentifier(text);
    }

    // ------------------------------------------------------------ columnas

    @ParameterizedTest
    @CsvSource({
            "firstName,first_name",
            "createdAt,created_at",
            "userID,user_id",
            "name,name",
            "already_snake,already_snake",
            "ALLCAPS,allcaps"
    })
    void columnNameIsConvertedToSnakeCase(String input, String expected) {
        assertEquals(expected, strategy.toPhysicalColumnName(id(input), null).getText());
    }

    @Test
    void columnNameNullReturnsNull() {
        assertNull(strategy.toPhysicalColumnName(null, null));
    }

    // -------------------------------------------------------------- tablas

    @ParameterizedTest
    @CsvSource({
            "User,users",
            "UserProfile,user_profiles",
            "Category,categories",
            "Box,boxes",
            "users,users"
    })
    void tableNameIsPluralizedAndConvertedToSnakeCase(String input, String expected) {
        assertEquals(expected, strategy.toPhysicalTableName(id(input), null).getText());
    }

    // ------------------------------------------------------------- catalog

    @Test
    void catalogNameIsConvertedToSnakeCase() {
        assertEquals("my_catalog", strategy.toPhysicalCatalogName(id("myCatalog"), null).getText());
    }

    @Test
    void catalogNameNullReturnsNull() {
        assertNull(strategy.toPhysicalCatalogName(null, null));
    }

    // -------------------------------------------------------------- schema

    @Test
    void schemaNameIsConvertedToSnakeCase() {
        assertEquals("my_schema", strategy.toPhysicalSchemaName(id("MySchema"), null).getText());
    }

    @Test
    void schemaNameNullReturnsNull() {
        assertNull(strategy.toPhysicalSchemaName(null, null));
    }

    // ----------------------------------------------------------- secuencia

    @Test
    void sequenceNameIsConvertedToSnakeCase() {
        assertEquals("user_seq", strategy.toPhysicalSequenceName(id("userSeq"), null).getText());
    }

    @Test
    void sequenceNameNullReturnsNull() {
        assertNull(strategy.toPhysicalSequenceName(null, null));
    }
}