package com.resolum.alivia.iam.domain.model.valueobjects;

/**
 * Value object that stores a password hash, never a plain-text password.
 *
 * @param value the encoded password
 */
public record PasswordHash(String value) {

    /**
     * Creates a password hash value object.
     *
     * @param value the encoded password
     * @throws IllegalArgumentException if the value is null or blank
     */
    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Password hash is required.");
        }
    }
}
