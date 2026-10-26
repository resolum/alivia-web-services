package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailVerificationTokenTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.parse("2026-10-24T12:00:00");

    @Test
    void createsTokenWithHashAndExpiration() {
        var token = new EmailVerificationToken("sha256-hash", EXPIRES_AT);

        assertEquals("sha256-hash", token.value());
        assertEquals(EXPIRES_AT, token.expiresAt());
    }

    @Test
    void rejectsNullAndBlankHash() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmailVerificationToken(null, EXPIRES_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new EmailVerificationToken("   ", EXPIRES_AT));
    }

    @Test
    void rejectsNullExpiration() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmailVerificationToken("sha256-hash", null));
    }

    @Test
    void isExpiredAtUsesExpirationBoundary() {
        var token = new EmailVerificationToken("sha256-hash", EXPIRES_AT);

        assertFalse(token.isExpiredAt(EXPIRES_AT.minusNanos(1)));
        assertTrue(token.isExpiredAt(EXPIRES_AT));
        assertTrue(token.isExpiredAt(EXPIRES_AT.plusSeconds(1)));
    }

    @Test
    void isExpiredAtRejectsNullMoment() {
        var token = new EmailVerificationToken("sha256-hash", EXPIRES_AT);

        assertThrows(IllegalArgumentException.class, () -> token.isExpiredAt(null));
    }
}
