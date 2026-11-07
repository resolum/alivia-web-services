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
     * Generates a random 6-digit code for an account. The stored hash includes the account
     * identifier, so two accounts with the same code never share a hash.
     *
     * @param accountId the public account identifier
     * @return the plain-text code and its hashed domain representation
     */
    GeneratedToken generateCode(String accountId);

    /**
     * Compares a code typed by the user with the hash stored for that account, in constant time.
     *
     * @param accountId    the public account identifier
     * @param code         the plain-text code
     * @param expectedHash the stored hash
     * @return {@code true} when the code belongs to that account
     */
    boolean matchesCode(String accountId, String code, String expectedHash);

    /**
     * Builds the verification link that is sent to the user.
     *
     * @param rawToken the plain-text token
     * @return the link with the token appended as a query parameter
     */
    String buildVerificationLink(String rawToken);

    /**
     * Calculates the SHA-256 hash of a plain-text token.
     *
     * @param rawToken the plain-text token
     * @return the hash encoded as lowercase hexadecimal
     */
    String hash(String rawToken);

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
