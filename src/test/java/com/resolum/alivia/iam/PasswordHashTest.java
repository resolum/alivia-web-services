package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordHashTest {

    @Test
    void createsValidPasswordHash() {
        var hash = new PasswordHash("$2a$12$somehashvalue");
        assertEquals("$2a$12$somehashvalue", hash.value());
    }

    @Test
    void rejectsNullOrBlankPasswordHash() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHash(null));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHash("   "));
    }
}
