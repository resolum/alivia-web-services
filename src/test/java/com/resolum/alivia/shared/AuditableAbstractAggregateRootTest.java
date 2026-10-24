package com.resolum.alivia.shared;


import com.resolum.alivia.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditableAbstractAggregateRootTest {

    static class SampleAggregate extends AuditableAbstractAggregateRoot<SampleAggregate> {
        Collection<Object> events() {
            return domainEvents();
        }
    }

    @Test
    void isAbstractMappedSuperclassWithAuditingListener() {
        Class<?> type = AuditableAbstractAggregateRoot.class;

        assertTrue(Modifier.isAbstract(type.getModifiers()));
        assertTrue(type.isAnnotationPresent(MappedSuperclass.class));
        EntityListeners listeners = type.getAnnotation(EntityListeners.class);
        assertEquals(AuditingEntityListener.class, listeners.value()[0]);
    }

    @Test
    void idIsGeneratedByIdentity() throws NoSuchFieldException {
        Field id = AuditableAbstractAggregateRoot.class.getDeclaredField("id");

        assertTrue(id.isAnnotationPresent(Id.class));
        assertEquals(GenerationType.IDENTITY, id.getAnnotation(GeneratedValue.class).strategy());
    }

    @Test
    void createdAtIsAuditedNotNullAndNotUpdatable() throws NoSuchFieldException {
        Field createdAt = AuditableAbstractAggregateRoot.class.getDeclaredField("createdAt");
        Column column = createdAt.getAnnotation(Column.class);

        assertTrue(createdAt.isAnnotationPresent(CreatedDate.class));
        assertFalse(column.nullable());
        assertFalse(column.updatable());
    }

    @Test
    void updatedAtIsAuditedAndNotNull() throws NoSuchFieldException {
        Field updatedAt = AuditableAbstractAggregateRoot.class.getDeclaredField("updatedAt");

        assertTrue(updatedAt.isAnnotationPresent(LastModifiedDate.class));
        assertFalse(updatedAt.getAnnotation(Column.class).nullable());
    }

    @Test
    void gettersReturnFieldValues() {
        var aggregate = new SampleAggregate();
        LocalDateTime created = LocalDateTime.of(2026, 10, 24, 10, 0);
        LocalDateTime updated = created.plusHours(1);
        ReflectionTestUtils.setField(aggregate, "id", 7L);
        ReflectionTestUtils.setField(aggregate, "createdAt", created);
        ReflectionTestUtils.setField(aggregate, "updatedAt", updated);

        assertEquals(7L, aggregate.getId());
        assertEquals(created, aggregate.getCreatedAt());
        assertEquals(updated, aggregate.getUpdatedAt());
    }

    @Test
    void newInstanceHasNoAuditValuesYet() {
        var aggregate = new SampleAggregate();

        assertNull(aggregate.getId());
        assertNull(aggregate.getCreatedAt());
        assertNull(aggregate.getUpdatedAt());
    }

    @Test
    void addDomainEventRegistersEvent() {
        var aggregate = new SampleAggregate();
        Object event = new Object();

        aggregate.addDomainEvent(event);

        assertEquals(1, aggregate.events().size());
        assertTrue(aggregate.events().contains(event));
    }

    @Test
    void addDomainEventKeepsAllEventsInOrder() {
        var aggregate = new SampleAggregate();

        aggregate.addDomainEvent("first");
        aggregate.addDomainEvent("second");

        assertEquals(java.util.List.of("first", "second"), java.util.List.copyOf(aggregate.events()));
    }
}
