package com.resolum.alivia.iam.domain.exceptions;

/**
 * Thrown when the Profiles context or the verification email provider is unavailable.
 *
 * <p>The REST layer translates this exception to a generic service-unavailable
 * response and must not expose its cause.</p>
 */
public class RegistrationUnavailableException extends RuntimeException {

    /**
     * Creates the exception with the original infrastructure failure.
     *
     * @param cause the infrastructure failure
     */
    public RegistrationUnavailableException(Throwable cause) {
        super("Registration is temporarily unavailable.", cause);
    }
}
