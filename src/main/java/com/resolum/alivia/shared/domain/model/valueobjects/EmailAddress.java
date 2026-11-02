package com.resolum.alivia.shared.domain.model.valueobjects;

import java.util.Locale;

/**
 * Value object that represents a normalized email address.
 *
 * <p>Normalization trims surrounding whitespace and lowercases the value. Validation
 * is performed without regular expressions.</p>
 *
 * @param email the normalized email address
 */
public record EmailAddress(String email) {

    private static final char AT_SIGN = '@';
    private static final char DOT = '.';

    /**
     * Creates a normalized and validated email address.
     *
     * @param email the raw email address
     * @throws IllegalArgumentException if the email address is missing, contains whitespace,
     *                                  does not have exactly one {@code @}, has an empty local
     *                                  part or does not have a valid domain
     */
    public EmailAddress {
        email = normalize(email);
        validate(email);
    }

    private static String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email address is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validate(String email) {
        if (email.isEmpty() || containsWhitespace(email)) {
            throw new IllegalArgumentException("Email address is invalid.");
        }
        int atIndex = email.indexOf(AT_SIGN);
        if (atIndex <= 0 || atIndex != email.lastIndexOf(AT_SIGN)) {
            throw new IllegalArgumentException("Email address is invalid.");
        }
        String domain = email.substring(atIndex + 1);
        if (domain.isEmpty() || domain.indexOf(DOT) < 0 || domain.startsWith(".")
                || domain.endsWith(".")) {
            throw new IllegalArgumentException("Email address is invalid.");
        }
    }

    private static boolean containsWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }
}
