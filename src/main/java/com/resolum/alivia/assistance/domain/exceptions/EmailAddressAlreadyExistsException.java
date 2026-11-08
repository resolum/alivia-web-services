package com.resolum.alivia.assistance.domain.exceptions;

public class EmailAddressAlreadyExistsException extends RuntimeException {
    public EmailAddressAlreadyExistsException(String email) {
        super("Email address " + email + " already exists.");
    }
}
