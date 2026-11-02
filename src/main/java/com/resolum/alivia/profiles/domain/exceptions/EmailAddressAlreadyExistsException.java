package com.resolum.alivia.profiles.domain.exceptions;

import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;

public class EmailAddressAlreadyExistsException extends RuntimeException {
    public EmailAddressAlreadyExistsException(EmailAddress emailAddress) {
        this(emailAddress.email());
    }

    public EmailAddressAlreadyExistsException(String email) {
        super("Email address " + email + " already exists");
    }
}
