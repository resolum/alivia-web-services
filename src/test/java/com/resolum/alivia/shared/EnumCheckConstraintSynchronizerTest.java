package com.resolum.alivia.shared;

import com.resolum.alivia.hcm.domain.model.aggregates.WorkSchedule;
import com.resolum.alivia.hcm.domain.model.entities.ContractVersion;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.schema.EnumCheckConstraint;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.schema.EnumCheckConstraintSynchronizer;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnumCheckConstraintSynchronizerTest {

    @Test
    void buildsReplaceStatementsFromTheEnumNames() {
        var constraint = new EnumCheckConstraint("contract_versions", "action", List.of("CREATED", "ACTIVATED"));

        assertEquals(List.of(
                "ALTER TABLE contract_versions DROP CONSTRAINT IF EXISTS contract_versions_action_check",
                "ALTER TABLE contract_versions ADD CONSTRAINT contract_versions_action_check CHECK (action IN ('CREATED', 'ACTIVATED'))"),
                constraint.replaceStatements());
    }

    @Test
    void findsEveryStringEnumColumnInTheMetamodel() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:enum-checks;MODE=PostgreSQL")
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.physical_naming_strategy",
                        "com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.strategy.SnakeCaseWithPluralizedTablePhysicalNamingStrategy")
                .build();
        try (var sessionFactory = new MetadataSources(registry).addAnnotatedClass(ContractVersion.class).addAnnotatedClass(WorkSchedule.class)
                .buildMetadata().buildSessionFactory()) {
            var constraints = invokeEnumConstraints(sessionFactory);

            var action = constraints.stream().filter(item -> item.column().equals("action")).findFirst().orElseThrow();
            assertEquals("contract_versions", action.table());
            assertTrue(action.values().contains("ACTIVATED"));
            assertTrue(constraints.stream().anyMatch(item -> item.table().equals("work_schedule_shifts")
                    && item.column().equals("day_of_week") && item.values().contains("SUNDAY")));
        }
    }

    @Test
    void skipsDatabasesOtherThanPostgreSql() throws Exception {
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        var entityManagerFactory = mock(EntityManagerFactory.class);

        new EnumCheckConstraintSynchronizer(entityManagerFactory, dataSource, true).synchronize();
        new EnumCheckConstraintSynchronizer(entityManagerFactory, dataSource, false).synchronize();

        verifyNoInteractions(entityManagerFactory);
        verify(dataSource, times(1)).getConnection();
    }

    @SuppressWarnings("unchecked")
    private static List<EnumCheckConstraint> invokeEnumConstraints(EntityManagerFactory factory) {
        try {
            var method = EnumCheckConstraintSynchronizer.class.getDeclaredMethod("enumConstraints", EntityManagerFactory.class);
            method.setAccessible(true);
            return (List<EnumCheckConstraint>) method.invoke(null, factory);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
