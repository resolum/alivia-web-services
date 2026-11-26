package com.resolum.alivia.profiles.interfaces.rest;

import com.resolum.alivia.profiles.domain.exceptions.ProfileAccessDeniedException;
import org.springframework.security.core.Authentication;

/** Who is calling, read from the authentication and never from the request. */
public final class Caller {

    private Caller() {
    }

    /** @throws ProfileAccessDeniedException when the caller is not a user account */
    public static Long userId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ProfileAccessDeniedException("Authentication is required.");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ProfileAccessDeniedException("The caller is not a user account.");
        }
    }

    public static boolean isAdministrator(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMINISTRATOR".equals(authority.getAuthority()));
    }
}
