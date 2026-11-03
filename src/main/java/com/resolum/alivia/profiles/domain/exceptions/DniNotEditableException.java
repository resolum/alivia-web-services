package com.resolum.alivia.profiles.domain.exceptions;

/**
 * Thrown when a client tries to change the DNI from the profile endpoint.
 *
 * <p>The DNI identifies the person and is fixed after registration.</p>
 */
public class DniNotEditableException extends RuntimeException {

    public DniNotEditableException() {
        super("The DNI cannot be changed from the profile.");
    }
}
