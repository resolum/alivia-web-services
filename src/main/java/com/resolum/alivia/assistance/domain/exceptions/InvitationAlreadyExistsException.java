package com.resolum.alivia.assistance.domain.exceptions;

public class InvitationAlreadyExistsException extends RuntimeException {
    public InvitationAlreadyExistsException(String email) {
        super("A pending invitation already exists for " + email);
    }
}
