package com.resolum.alivia.iam;

import com.resolum.alivia.iam.infrastructure.tokens.jwt.services.TokenServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
    private final TokenServiceImpl service = new TokenServiceImpl(CLOCK, 24, "https://alivia.app/verify");

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new TokenServiceImpl(CLOCK, -1, "https://alivia.app/verify"));
        assertThrows(IllegalArgumentException.class, () -> new TokenServiceImpl(CLOCK, 24, null));
        assertThrows(IllegalArgumentException.class, () -> new TokenServiceImpl(CLOCK, 24, "   "));
    }

    @Test
    void generatesValidToken() {
        var generated = service.generate();

        assertNotNull(generated.rawToken());
        assertFalse(generated.rawToken().isBlank());
        assertNotNull(generated.token());
        assertNotNull(generated.token().value());
        assertEquals(LocalDateTime.now(CLOCK).plusHours(24), generated.token().expiresAt());
    }

    @Test
    void buildsVerificationLink() {
        String link = service.buildVerificationLink("abc-123+xyz");
        assertEquals("https://alivia.app/verify?token=abc-123%2Bxyz", link);

        assertThrows(IllegalArgumentException.class, () -> service.buildVerificationLink(null));
        assertThrows(IllegalArgumentException.class, () -> service.buildVerificationLink("  "));
    }

    @Test
    void hashesAndMatchesTokens() {
        String raw = "sample-token-123";
        String hash = service.hash(raw);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(service.matches(raw, hash));
        assertFalse(service.matches("other-token", hash));
        assertFalse(service.matches(null, hash));
        assertFalse(service.matches(raw, null));

        assertThrows(IllegalArgumentException.class, () -> service.hash(null));
        assertThrows(IllegalArgumentException.class, () -> service.hash("  "));
    }
}
