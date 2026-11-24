package com.resolum.alivia.assistance.interfaces.rest;

import com.resolum.alivia.assistance.domain.exceptions.AssistanceAccessDeniedException;

import java.security.Principal;

/**
 * Reads the account of the caller from the authenticated principal.
 *
 * <p>The id never comes from the request: a body field or a query parameter that names a user, a relative or a
 * caregiver is never trusted to say who is calling.</p>
 */
public final class CallerIdentity {

    private CallerIdentity() {
    }

    /**
     * @return the id of the authenticated user
     * @throws AssistanceAccessDeniedException when there is no principal or it is not a user account
     */
    public static Long requireUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AssistanceAccessDeniedException("Authentication is required.");
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException exception) {
            throw new AssistanceAccessDeniedException("The caller is not a user account.");
        }
    }
}
