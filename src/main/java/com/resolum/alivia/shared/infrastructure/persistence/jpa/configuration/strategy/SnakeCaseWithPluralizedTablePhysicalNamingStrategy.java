package com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.strategy;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import static io.github.encryptorcode.pluralize.Pluralize.pluralize;

public class SnakeCaseWithPluralizedTablePhysicalNamingStrategy extends PhysicalNamingStrategySnakeCaseImpl {
    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
        Identifier snakeCaseName = super.toPhysicalTableName(name, context);
        if (snakeCaseName == null || snakeCaseName.isQuoted()) {
            return snakeCaseName;
        }
        return Identifier.toIdentifier(pluralize(snakeCaseName.getText()), false);
    }
}
