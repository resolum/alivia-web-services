package com.resolum.alivia.shared;

import com.resolum.alivia.shared.domain.model.entities.AuditableModel;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditableModelTest {

    static class SampleEntity extends AuditableModel {
    }

    @Test
    void isAbstractMappedSuperclassWithAuditingListener() {
        Class<?> type = AuditableModel.class;

        assertTrue(Modifier.isAbstract(type.getModifiers()));
        assertTrue(type.isAnnotationPresent(MappedSuperclass.class));
        assertEquals(AuditingEntityListener.class, type.getAnnotation(EntityListeners.class).value()[0]);
    }

    @Test
    void idIsGeneratedByIdentity() throws NoSuchFieldException {
        Field id = AuditableModel.class.getDeclaredField("id");

        assertTrue(id.isAnnotationPresent(Id.class));
        assertEquals(GenerationType.IDENTITY, id.getAnnotation(GeneratedValue.class).strategy());
    }

    @Test
    void createdAtIsAuditedNotNullAndNotUpdatable() throws NoSuchFieldException {
        Field createdAt = AuditableModel.class.getDeclaredField("createdAt");
        Column column = createdAt.getAnnotation(Column.class);

        assertTrue(createdAt.isAnnotationPresent(CreatedDate.class));
        assertFalse(column.nullable());
        assertFalse(column.updatable());
    }

    @Test
    void updatedAtIsAuditedAndNotNull() throws NoSuchFieldException {
        Field updatedAt = AuditableModel.class.getDeclaredField("updatedAt");

        assertTrue(updatedAt.isAnnotationPresent(LastModifiedDate.class));
        assertFalse(updatedAt.getAnnotation(Column.class).nullable());
    }

    @Test
    void gettersReturnFieldValues() {
        var entity = new SampleEntity();
        LocalDateTime created = LocalDateTime.of(2026, 10, 24, 8, 0);
        LocalDateTime updated = created.plusDays(1);
        ReflectionTestUtils.setField(entity, "id", 3L);
        ReflectionTestUtils.setField(entity, "createdAt", created);
        ReflectionTestUtils.setField(entity, "updatedAt", updated);

        assertEquals(3L, entity.getId());
        assertEquals(created, entity.getCreatedAt());
        assertEquals(updated, entity.getUpdatedAt());
    }

    @Test
    void newInstanceHasNoAuditValuesYet() {
        var entity = new SampleEntity();

        assertNull(entity.getId());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
    }
}
