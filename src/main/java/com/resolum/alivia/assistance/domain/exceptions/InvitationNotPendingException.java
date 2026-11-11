package com.resolum.alivia.assistance.domain.exceptions;

public class InvitationNotPendingException extends RuntimeException {
    public InvitationNotPendingException() {
        super("The invitation is not pending.");
    }
}
