package com.resolum.alivia.iam.application.internal.outboundservices.tokens;

import com.resolum.alivia.iam.domain.model.valueobjects.EmailVerificationToken;

/**
 * Outbound port for generating and comparing email verification tokens.
 *
 * <p>Implementations must generate unpredictable tokens, store only their SHA-256
 * hash and compare hashes in constant time.</p>
 */
public interface EmailVerificationTokenService {

    /**
     * Generates a new plain-text token together with its hashed domain representation
     * and expiration moment.
     *
     * @return the generated token
     */
    GeneratedToken generate();

    /**
     * Builds the verification link that is sent to the user.
     *
     * @param rawToken the plain-text token
     * @return the link with the token appended as a query parameter
     */
    String buildVerificationLink(String rawToken);

    /**
     * Compares a plain-text token with an expected SHA-256 hash in constant time.
     *
     * @param rawToken     the plain-text token
     * @param expectedHash the expected hexadecimal hash
     * @return {@code true} when both values match
     */
    boolean matches(String rawToken, String expectedHash);

    /**
     * Result of generating a verification token.
     *
     * @param rawToken the plain-text token sent to the user
     * @param token    the domain token that contains only the hash and the expiration
     */
    record GeneratedToken(String rawToken, EmailVerificationToken token) {

        /**
         * Creates a generated token result.
         *
         * @param rawToken the plain-text token
         * @param token    the domain token
         * @throws IllegalArgumentException if either value is null or the raw token is blank
         */
        public GeneratedToken {
            if (rawToken == null || rawToken.isBlank() || token == null) {
                throw new IllegalArgumentException("Generated verification token is invalid.");
            }
        }
    }
}
