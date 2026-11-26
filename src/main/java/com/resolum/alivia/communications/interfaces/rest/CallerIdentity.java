package com.resolum.alivia.communications.interfaces.rest;

import com.resolum.alivia.communications.domain.exceptions.CallerNotIdentified;

import java.security.Principal;

/**
 * Reads the account of the caller from the authenticated principal. The id never comes from the request: a
 * parameter or a body field that names a user is never trusted to say who is calling.
 */
public final class CallerIdentity {

    private CallerIdentity() {
    }

    public static Long requireUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new CallerNotIdentified();
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException exception) {
            throw new CallerNotIdentified();
        }
    }
}
