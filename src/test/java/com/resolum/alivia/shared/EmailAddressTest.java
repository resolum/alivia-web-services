package com.resolum.alivia.shared;

import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailAddressTest {

    @Test
    void normalizesWhitespaceAndCase() {
        assertEquals("renato@example.com", new EmailAddress("  Renato@Example.COM  ").email());
    }

    @Test
    void acceptsAValidAddress() {
        assertDoesNotThrow(() -> new EmailAddress("a@b.co"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("   "));
    }

    @Test
    void rejectsAddressWithoutAtSign() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("renato.example.com"));
    }

    @Test
    void rejectsAddressWithMoreThanOneAtSign() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("renato@example@com"));
    }

    @Test
    void rejectsEmptyLocalPart() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("@example.com"));
    }

    @Test
    void rejectsEmptyDomain() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("renato@"));
    }

    @Test
    void rejectsDomainWithoutDot() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("renato@example"));
    }

    @Test
    void rejectsDomainStartingWithDot() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("renato@.example.com"));
    }

    @Test
    void rejectsDomainEndingWithDot() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("renato@example.com."));
    }

    @Test
    void rejectsAddressWithWhitespaceInside() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAddress("renato @example.com"));
    }

    @Test
    void equalityUsesTheNormalizedValue() {
        assertEquals(new EmailAddress("A@B.COM"), new EmailAddress("a@b.com"));
        assertNotEquals(new EmailAddress("a@b.com"), new EmailAddress("c@d.com"));
    }
}
