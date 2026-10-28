package com.resolum.alivia.shared.infrastructure.persistence.jpa.schema;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the enum check constraints of PostgreSQL in step with the Java enums.
 *
 * <p>With {@code ddl-auto=update}, Hibernate creates {@code CHECK (column IN (...))} for every
 * {@code @Enumerated(STRING)} column when the table is first created, but never updates it. A value
 * added later to the enum (for example a new contract action) is then rejected by the database.
 * At startup this component rewrites those constraints from the current enums, so no manual
 * migration is needed. It is a stopgap until Flyway (AP-480) manages the schema.</p>
 */
@Slf4j
@Component
public class EnumCheckConstraintSynchronizer {
    private final EntityManagerFactory entityManagerFactory;
    private final DataSource dataSource;
    private final boolean enabled;

    public EnumCheckConstraintSynchronizer(EntityManagerFactory entityManagerFactory, DataSource dataSource,
                                           @Value("${alivia.persistence.sync-enum-checks:true}") boolean enabled) {
        this.entityManagerFactory = entityManagerFactory;
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void synchronize() {
        if (!enabled || !isPostgreSql()) return;
        var jdbc = new JdbcTemplate(dataSource);
        for (var constraint : enumConstraints(entityManagerFactory)) {
            try {
                constraint.replaceStatements().forEach(jdbc::execute);
            } catch (RuntimeException exception) {
                // Rows with a value the enum no longer has: keep the old constraint and report it.
                log.warn("Could not refresh check constraint {}: {}", constraint.name(), exception.getMessage());
            }
        }
    }

    /** One constraint per varchar column mapped to an enum, read from the Hibernate metamodel. */
    static List<EnumCheckConstraint> enumConstraints(EntityManagerFactory entityManagerFactory) {
        var constraints = new LinkedHashMap<String, EnumCheckConstraint>();
        entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel()
                .forEachEntityDescriptor(entity -> entity.forEachAttributeMapping(
                        attribute -> collect(attribute, constraints)));
        return new ArrayList<>(constraints.values());
    }

    private static void collect(ModelPart part, Map<String, EnumCheckConstraint> constraints) {
        if (part instanceof PluralAttributeMapping collection) {
            // Element collections keep their values in their own table, such as the shifts of a schedule.
            collect(collection.getElementDescriptor(), constraints);
            return;
        }
        if (part instanceof EmbeddableValuedModelPart embedded) {
            embedded.getEmbeddableTypeDescriptor().forEachAttributeMapping(nested -> collect(nested, constraints));
            return;
        }
        if (!(part instanceof BasicValuedModelPart column)) return;
        var type = part.getJavaType().getJavaTypeClass();
        var jdbcMapping = column.getJdbcMapping();
        if (!type.isEnum() || !jdbcMapping.getJdbcType().isString() || jdbcMapping.getValueConverter() != null) return;
        var values = Arrays.stream(type.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList();
        var constraint = new EnumCheckConstraint(column.getContainingTableExpression(), column.getSelectionExpression(), values);
        constraints.putIfAbsent(constraint.name(), constraint);
    }

    private boolean isPostgreSql() {
        try (var connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            log.warn("Could not read the database product: {}", exception.getMessage());
            return false;
        }
    }
}
