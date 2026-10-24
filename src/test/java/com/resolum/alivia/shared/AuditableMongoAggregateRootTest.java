package com.resolum.alivia.shared;

import com.resolum.alivia.shared.domain.model.aggregates.AuditableMongoAggregateRoot;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditableMongoAggregateRootTest {

    static class SampleDocument extends AuditableMongoAggregateRoot<SampleDocument> {
        Collection<Object> events() {
            return domainEvents();
        }
    }

    @Test
    void isAbstract() {
        assertTrue(Modifier.isAbstract(AuditableMongoAggregateRoot.class.getModifiers()));
    }

    @Test
    void idUsesSpringDataIdAnnotationAndIsString() throws NoSuchFieldException {
        Field id = AuditableMongoAggregateRoot.class.getDeclaredField("id");

        assertTrue(id.isAnnotationPresent(Id.class));
        assertEquals(String.class, id.getType());
    }

    @Test
    void auditFieldsAreAnnotated() throws NoSuchFieldException {
        Field createdAt = AuditableMongoAggregateRoot.class.getDeclaredField("createdAt");
        Field updatedAt = AuditableMongoAggregateRoot.class.getDeclaredField("updatedAt");

        assertTrue(createdAt.isAnnotationPresent(CreatedDate.class));
        assertTrue(updatedAt.isAnnotationPresent(LastModifiedDate.class));
    }

    @Test
    void gettersReturnFieldValues() {
        var document = new SampleDocument();
        LocalDateTime created = LocalDateTime.of(2026, 10, 24, 9, 30);
        LocalDateTime updated = created.plusMinutes(15);
        ReflectionTestUtils.setField(document, "id", "abc123");
        ReflectionTestUtils.setField(document, "createdAt", created);
        ReflectionTestUtils.setField(document, "updatedAt", updated);

        assertEquals("abc123", document.getId());
        assertEquals(created, document.getCreatedAt());
        assertEquals(updated, document.getUpdatedAt());
    }

    @Test
    void newInstanceHasNoAuditValuesYet() {
        var document = new SampleDocument();

        assertNull(document.getId());
        assertNull(document.getCreatedAt());
        assertNull(document.getUpdatedAt());
    }

    @Test
    void addDomainEventRegistersEvent() {
        var document = new SampleDocument();
        Object event = new Object();

        document.addDomainEvent(event);

        assertEquals(1, document.events().size());
        assertTrue(document.events().contains(event));
    }

    @Test
    void addDomainEventKeepsAllEventsInOrder() {
        var document = new SampleDocument();

        document.addDomainEvent("first");
        document.addDomainEvent("second");

        assertEquals(List.of("first", "second"), List.copyOf(document.events()));
    }
}