package com.resolum.alivia.iam.domain.exceptions;

import java.util.List;

/**
 * Thrown when a sign-up command violates a registration rule.
 *
 * <p>Each violation identifies the request field and the stable code that the REST
 * layer exposes to clients.</p>
 */
public class SignUpValidationException extends RuntimeException {

    /** The field is missing or blank. */
    public static final String REQUIRED = "required";
    /** The email address does not have a valid format. */
    public static final String INVALID_EMAIL = "invalid_email";
    /** The password does not satisfy the password policy. */
    public static final String PASSWORD_WEAK = "password_weak";
    /** The terms and conditions were not accepted. */
    public static final String TERMS_NOT_ACCEPTED = "terms_not_accepted";
    /** The value exceeds the maximum allowed length. */
    public static final String TOO_LONG = "too_long";

    private final List<FieldViolation> violations;

    /**
     * Creates an exception with one or more field violations.
     *
     * @param violations the violated fields and their codes
     */
    public SignUpValidationException(List<FieldViolation> violations) {
        super("Sign-up data is invalid.");
        if (violations == null || violations.isEmpty()) {
            throw new IllegalArgumentException("At least one violation is required.");
        }
        this.violations = List.copyOf(violations);
    }

    /**
     * Creates an exception with a single field violation.
     *
     * @param field the request field
     * @param code  the stable violation code
     */
    public SignUpValidationException(String field, String code) {
        this(List.of(new FieldViolation(field, code)));
    }

    /**
     * Returns the field violations.
     *
     * @return an immutable list of violations
     */
    public List<FieldViolation> violations() {
        return violations;
    }

    /**
     * A single field violation.
     *
     * @param field the request field
     * @param code  the stable violation code
     */
    public record FieldViolation(String field, String code) {

        /**
         * Creates a field violation.
         *
         * @param field the request field
         * @param code  the stable violation code
         * @throws IllegalArgumentException if either value is null or blank
         */
        public FieldViolation {
            if (field == null || field.isBlank() || code == null || code.isBlank()) {
                throw new IllegalArgumentException("Field and code are required.");
            }
        }
    }
}
