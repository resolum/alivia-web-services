package com.resolum.alivia.communications.domain.exceptions;

/** The request does not come from a user account, so there is no one whose notifications or devices to use. */
public class CallerNotIdentified extends RuntimeException {
    public CallerNotIdentified() {
        super("The caller is not an authenticated user account.");
    }
}
