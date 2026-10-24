package com.resolum.alivia.shared.domain.model.valueobjects;

public record EmailAddress(String email) {
    public EmailAddress {
        if (email == null || !email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Email address is invalid");
        }
    }
}
