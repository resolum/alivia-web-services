package com.resolum.alivia.tracking.interfaces.rest.transform;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/** Reads the user that calls from its authentication; the id never comes from the request. */
public final class UserIdFromAuthentication {

    private UserIdFromAuthentication() {
    }

    /**
     * IAM names an authenticated user by their numeric id. Anything else, an edge service included, is refused.
     */
    public static Long of(Authentication authentication) {
        var name = authentication == null ? null : authentication.getName();
        if (name != null) {
            try {
                var userId = Long.parseLong(name);
                if (userId > 0) return userId;
            } catch (NumberFormatException ignored) {
                // falls through
            }
        }
        throw new AccessDeniedException("The caller is not a user account.");
    }
}
