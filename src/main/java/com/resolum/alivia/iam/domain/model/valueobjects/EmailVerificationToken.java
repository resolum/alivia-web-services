package com.resolum.alivia.iam.domain.model.valueobjects;

import java.time.LocalDateTime;

/**
 * Value object that represents a pending email verification token.
 *
 * <p>The {@code value} is the SHA-256 hash of the token sent to the user, never the
 * token itself.</p>
 *
 * @param value     the SHA-256 hash of the verification token
 * @param expiresAt the moment after which the token is no longer valid
 */
public record EmailVerificationToken(String value, LocalDateTime expiresAt) {

    /**
     * Creates an email verification token.
     *
     * @param value     the SHA-256 hash of the verification token
     * @param expiresAt the expiration moment
     * @throws IllegalArgumentException if the value is null or blank, or the expiration is null
     */
    public EmailVerificationToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email verification token hash is required.");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Email verification expiration is required.");
        }
    }

    /**
     * Indicates whether the token is expired at the given moment.
     *
     * @param now the moment to compare against
     * @return {@code true} when the token is expired
     * @throws IllegalArgumentException if {@code now} is null
     */
    public boolean isExpiredAt(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("Current time is required.");
        }
        return !now.isBefore(expiresAt);
    }
}
