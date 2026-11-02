package com.resolum.alivia.shared;

import com.resolum.alivia.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuditableAbstractPersistenceEntityTest {

    private static class ConcreteEntity extends AuditableAbstractPersistenceEntity {}

    @Test
    void gettersAndSettersWorkCorrectly() {
        var entity = new ConcreteEntity();
        var now = LocalDateTime.now();

        entity.setId(10L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertEquals(10L, entity.getId());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
        assertNotNull(entity);
    }
}
