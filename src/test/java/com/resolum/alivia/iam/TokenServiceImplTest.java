package com.resolum.alivia.iam;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.domain.model.valueobjects.PasswordHash;
import com.resolum.alivia.iam.infrastructure.tokens.jwt.services.TokenServiceImpl;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET = "test-secret-with-at-least-32-bytes!!";
    private final TokenServiceImpl service = new TokenServiceImpl(CLOCK, 24, "https://alivia.app/verify", SECRET, 15, 7);

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new TokenServiceImpl(CLOCK, -1, "https://alivia.app/verify", SECRET, 15, 7));
        assertThrows(IllegalArgumentException.class, () -> new TokenServiceImpl(CLOCK, 24, null, SECRET, 15, 7));
        assertThrows(IllegalArgumentException.class, () -> new TokenServiceImpl(CLOCK, 24, "   ", SECRET, 15, 7));
        assertThrows(IllegalArgumentException.class, () -> new TokenServiceImpl(CLOCK, 24, "https://alivia.app/verify", "short", 15, 7));
        assertThrows(IllegalArgumentException.class, () -> new TokenServiceImpl(CLOCK, 24, "https://alivia.app/verify", null, 15, 7));
        assertThrows(IllegalArgumentException.class, () -> new TokenServiceImpl(CLOCK, 24, "https://alivia.app/verify", SECRET, 0, 7));
        assertThrows(IllegalArgumentException.class, () -> new TokenServiceImpl(CLOCK, 24, "https://alivia.app/verify", SECRET, 15, 0));
    }

    @Test
    void signsAccessTokenWithIdentityRoleAndPermissions() {
        var user = new User(new EmailAddress("maria.lopez@example.com"), new PasswordHash("bcrypt-hash"),
                AccessRole.CAREGIVER, LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(user, "id", 42L);

        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(CLOCK.instant()))
                .build()
                .parseSignedClaims(service.generateAccessToken(user))
                .getPayload();

        assertEquals("42", claims.getSubject());
        assertEquals("maria.lopez@example.com", claims.get("email"));
        assertEquals("CAREGIVER", claims.get("role"));
        assertEquals(List.of("ROLE_CAREGIVER"), claims.get("permissions"));
        assertEquals(CLOCK.instant().plusSeconds(900), claims.getExpiration().toInstant());
        assertEquals(900L, service.getAccessTokenExpirationSeconds());
    }

    @Test
    void readsUserIdFromOwnAccessTokenAndRejectsForeignOnes() {
        var user = new User(new EmailAddress("maria.lopez@example.com"), new PasswordHash("bcrypt-hash"),
                AccessRole.CAREGIVER, LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(user, "id", 42L);
        var foreign = new TokenServiceImpl(CLOCK, 24, "https://alivia.app/verify", "another-secret-with-at-least-32-bytes", 15, 7);

        assertEquals("42", service.getUserIdFromAccessToken(service.generateAccessToken(user)));
        assertThrows(RuntimeException.class, () -> service.getUserIdFromAccessToken(foreign.generateAccessToken(user)));
    }

    @Test
    void generatesHashedRefreshToken() {
        var generated = service.generateRefreshToken();

        assertFalse(generated.rawToken().isBlank());
        assertEquals(service.hashRefreshToken(generated.rawToken()), generated.hash());
        assertEquals(LocalDateTime.now(CLOCK).plusDays(7), generated.expiresAt());
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
