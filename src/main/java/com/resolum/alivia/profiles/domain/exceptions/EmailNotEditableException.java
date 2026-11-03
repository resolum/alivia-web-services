package com.resolum.alivia.profiles.domain.exceptions;

/**
 * Thrown when a client tries to change the email address from the profile endpoint.
 *
 * <p>The email is an IAM credential, so it is only changed through the email
 * verification flow.</p>
 */
public class EmailNotEditableException extends RuntimeException {

    public EmailNotEditableException() {
        super("The email address cannot be changed from the profile.");
    }
}
