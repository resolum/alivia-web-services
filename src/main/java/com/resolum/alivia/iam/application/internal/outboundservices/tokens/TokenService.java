package com.resolum.alivia.iam.application.internal.outboundservices.tokens;

import com.resolum.alivia.iam.domain.model.aggregates.User;

import java.time.LocalDateTime;

/**
 * Outbound port for session tokens: signed access tokens and opaque refresh tokens.
 */
public interface TokenService {

    /**
     * Signs an access token with the user identity, role and permissions.
     *
     * @param user the authenticated user
     * @return the signed access token
     */
    String generateAccessToken(User user);

    /**
     * Returns the lifetime of an access token.
     *
     * @return seconds until an access token expires
     */
    long getAccessTokenExpirationSeconds();

    /**
     * Verifies the signature and expiration of an access token.
     *
     * @param accessToken the signed access token
     * @return the user identifier stored in the token subject
     * @throws RuntimeException if the token is malformed, tampered or expired
     */
    String getUserIdFromAccessToken(String accessToken);

    /**
     * Generates a new opaque refresh token.
     *
     * @return the plain-text token with its hash and expiration
     */
    GeneratedRefreshToken generateRefreshToken();

    /**
     * Calculates the SHA-256 hash of a plain-text refresh token.
     *
     * @param rawToken the plain-text token
     * @return the hash encoded as lowercase hexadecimal
     */
    String hashRefreshToken(String rawToken);

    /**
     * Result of generating a refresh token.
     *
     * @param rawToken  the plain-text token sent to the client
     * @param hash      the SHA-256 hash stored on the user
     * @param expiresAt the expiration moment
     */
    record GeneratedRefreshToken(String rawToken, String hash, LocalDateTime expiresAt) {
    }
}
