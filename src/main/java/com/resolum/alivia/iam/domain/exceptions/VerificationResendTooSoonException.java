package com.resolum.alivia.iam.domain.exceptions;

/**
 * Thrown when a new verification email is requested before the resend cooldown ends.
 */
public class VerificationResendTooSoonException extends RuntimeException {

    private final long retryAfterSeconds;

    /**
     * Creates the exception.
     *
     * @param retryAfterSeconds the seconds left until a new request is allowed
     */
    public VerificationResendTooSoonException(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Returns the seconds left until a new request is allowed.
     *
     * @return the remaining wait in seconds
     */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
