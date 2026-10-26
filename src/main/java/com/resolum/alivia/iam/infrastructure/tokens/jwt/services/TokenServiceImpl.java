package com.resolum.alivia.iam.infrastructure.tokens.jwt.services;

import com.resolum.alivia.iam.domain.model.aggregates.User;
import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import com.resolum.alivia.iam.infrastructure.tokens.jwt.BearerTokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.SecretKey;

/**
 * Implementation of {@link BearerTokenService}.
 *
 * <p>Email verification and refresh tokens are cryptographically secure opaque tokens:
 * only their SHA-256 hash is stored and hashes are compared in constant time. Access
 * tokens are JWTs signed with HMAC-SHA256.</p>
 */
@Service
public class TokenServiceImpl implements BearerTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;
    private final int expirationHours;
    private final String linkBaseUrl;
    private final SecretKey signingKey;
    private final long accessTokenExpirationSeconds;
    private final int refreshTokenExpirationDays;

    /**
     * Creates the token service.
     *
     * @param clock                        the shared clock
     * @param expirationHours              number of hours a verification token remains valid
     * @param linkBaseUrl                  base URL of the frontend verification route
     * @param jwtSecret                    HMAC secret of at least 32 bytes
     * @param accessTokenExpirationMinutes number of minutes an access token remains valid
     * @param refreshTokenExpirationDays   number of days a refresh token remains valid
     */
    public TokenServiceImpl(
            Clock clock,
            @Value("${alivia.iam.email-verification.expiration-hours:24}") int expirationHours,
            @Value("${alivia.iam.email-verification.link-base-url}") String linkBaseUrl,
            @Value("${alivia.iam.jwt.secret}") String jwtSecret,
            @Value("${alivia.iam.jwt.access-token-expiration-minutes:15}") int accessTokenExpirationMinutes,
            @Value("${alivia.iam.jwt.refresh-token-expiration-days:7}") int refreshTokenExpirationDays) {
        if (expirationHours < 0) {
            throw new IllegalArgumentException("Verification token expiration must not be negative.");
        }
        if (linkBaseUrl == null || linkBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Verification link base URL is required.");
        }
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must have at least 32 bytes.");
        }
        if (accessTokenExpirationMinutes <= 0 || refreshTokenExpirationDays <= 0) {
            throw new IllegalArgumentException("Session token expirations must be positive.");
        }
        this.clock = clock;
        this.expirationHours = expirationHours;
        this.linkBaseUrl = linkBaseUrl.trim();
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationSeconds = accessTokenExpirationMinutes * 60L;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GeneratedToken generate() {
        var rawToken = generateRawToken();
        var expiresAt = LocalDateTime.now(clock).plusHours(expirationHours);
        return new GeneratedToken(rawToken, new EmailVerificationToken(sha256(rawToken), expiresAt));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String buildVerificationLink(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Verification token is required.");
        }
        return linkBaseUrl + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Verification token is required.");
        }
        return sha256(rawToken);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(String rawToken, String expectedHash) {
        if (rawToken == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256(rawToken).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateAccessToken(User user) {
        var issuedAt = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail().email())
                .claim("role", user.getAccessRole().name())
                // PENDING(contract): no capability matrix per role is defined yet, so the role is the only authority.
                .claim("permissions", List.of("ROLE_" + user.getAccessRole().name()))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(accessTokenExpirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserIdFromAccessToken(String accessToken) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(accessToken)
                .getPayload()
                .getSubject();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GeneratedRefreshToken generateRefreshToken() {
        var rawToken = generateRawToken();
        var expiresAt = LocalDateTime.now(clock).plusDays(refreshTokenExpirationDays);
        return new GeneratedRefreshToken(rawToken, sha256(rawToken), expiresAt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String hashRefreshToken(String rawToken) {
        return hash(rawToken);
    }

    private String generateRawToken() {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String rawToken) {
        try {
            var digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
