package com.resolum.alivia.assistance.domain.exceptions;

public class InvitationNotFoundException extends RuntimeException {
    public InvitationNotFoundException(Long id) {
        super("Invitation with ID " + id + " was not found.");
    }
}
