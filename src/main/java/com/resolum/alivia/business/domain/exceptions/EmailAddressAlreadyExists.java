package com.resolum.alivia.business.domain.exceptions;

public class EmailAddressAlreadyExists extends RuntimeException {
    public EmailAddressAlreadyExists() {
        super("Email address already exists");
    }

    public EmailAddressAlreadyExists(String email) {
        super("Email address " + email + " already exists");
    }
}
