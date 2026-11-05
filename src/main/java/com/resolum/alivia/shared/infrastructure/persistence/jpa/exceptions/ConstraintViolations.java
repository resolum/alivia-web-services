package com.resolum.alivia.shared.infrastructure.persistence.jpa.exceptions;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/**
 * Tells which database constraint a {@link DataIntegrityViolationException} broke, so a service can translate
 * only the violation it expects and let any other one surface as the error it is.
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /**
     * Whether the violation was raised by the named constraint. Uses the constraint name Hibernate extracted,
     * and falls back to the driver messages in the cause chain (e.g. PostgreSQL's
     * {@code duplicate key value violates unique constraint "<name>"}). The comparison ignores case.
     */
    public static boolean isViolationOf(DataIntegrityViolationException exception, String constraint) {
        if (exception == null || constraint == null || constraint.isBlank()) return false;
        var expected = constraint.toLowerCase(Locale.ROOT);
        for (Throwable cause = exception; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT).equals(expected)) {
                return true;
            }
            var message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("\"" + expected + "\"")) return true;
        }
        return false;
    }
}
