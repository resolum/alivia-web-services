package com.resolum.alivia.assistance.domain.exceptions;

public class InvitationExpiredException extends RuntimeException {
    public InvitationExpiredException() {
        super("The invitation has expired.");
    }
}
