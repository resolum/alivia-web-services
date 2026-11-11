package com.resolum.alivia.assistance.domain.exceptions;

public class InvitationNotForUserException extends RuntimeException {
    public InvitationNotForUserException() {
        super("The invitation does not belong to this user.");
    }
}
