package com.resolum.alivia.iam.domain.services;

import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;

import java.nio.charset.StandardCharsets;

/**
 * Password policy shared by the registration flow.
 *
 * <p>Rules: at least 8 characters, at most 72 UTF-8 bytes (BCrypt limit), one uppercase
 * letter, one lowercase letter, one digit and one special character from
 * {@code @ $ ! % * ? & #}. Validation is performed without regular expressions.</p>
 */
public final class PasswordPolicy {

    private static final int MIN_CHARACTERS = 8;
    private static final int MAX_BYTES = 72;
    private static final String SPECIAL_CHARACTERS = "@$!%*?&#";

    private PasswordPolicy() {
    }

    /**
     * Validates a plain-text password against the password policy.
     *
     * @param password the plain-text password
     * @throws SignUpValidationException with {@code too_long} when the UTF-8 representation
     *                                   exceeds 72 bytes, or {@code password_weak} when a
     *                                   rule is not satisfied
     */
    public static void validate(String password) {
        if (password == null) {
            throw weakPassword();
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new SignUpValidationException("password", SignUpValidationException.TOO_LONG);
        }
        if (password.codePointCount(0, password.length()) < MIN_CHARACTERS) {
            throw weakPassword();
        }
        if (!hasRequiredCharacters(password)) {
            throw weakPassword();
        }
    }

    private static boolean hasRequiredCharacters(String password) {
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (int index = 0; index < password.length(); index++) {
            char value = password.charAt(index);
            if (Character.isUpperCase(value)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(value)) {
                hasLowercase = true;
            } else if (Character.isDigit(value)) {
                hasDigit = true;
            }
            if (SPECIAL_CHARACTERS.indexOf(value) >= 0) {
                hasSpecial = true;
            }
        }
        return hasUppercase && hasLowercase && hasDigit && hasSpecial;
    }

    private static SignUpValidationException weakPassword() {
        return new SignUpValidationException("password", SignUpValidationException.PASSWORD_WEAK);
    }
}
