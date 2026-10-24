package com.resolum.alivia.iam.infrastructure.tokens.jwt.services;

import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;
import com.resolum.alivia.iam.infrastructure.tokens.jwt.BearerTokenService;
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
import java.util.HexFormat;

/**
 * Implementation of {@link BearerTokenService} for email verification tokens.
 *
 * <p>Generates cryptographically secure opaque tokens, stores only their SHA-256 hash
 * and compares hashes in constant time. PENDING(auth): JWT generation for login will be
 * added here once that flow is in scope.</p>
 */
@Service
public class TokenServiceImpl implements BearerTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;
    private final int expirationHours;
    private final String linkBaseUrl;

    /**
     * Creates the token service.
     *
     * @param clock           the shared clock
     * @param expirationHours number of hours a verification token remains valid
     * @param linkBaseUrl     base URL of the frontend verification route
     */
    public TokenServiceImpl(
            Clock clock,
            @Value("${alivia.iam.email-verification.expiration-hours:24}") int expirationHours,
            @Value("${alivia.iam.email-verification.link-base-url}") String linkBaseUrl) {
        if (expirationHours < 0) {
            throw new IllegalArgumentException("Verification token expiration must not be negative.");
        }
        if (linkBaseUrl == null || linkBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Verification link base URL is required.");
        }
        this.clock = clock;
        this.expirationHours = expirationHours;
        this.linkBaseUrl = linkBaseUrl.trim();
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
