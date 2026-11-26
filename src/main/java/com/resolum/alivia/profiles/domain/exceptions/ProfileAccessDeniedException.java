package com.resolum.alivia.profiles.domain.exceptions;

/** The authenticated user may not read or change the profile. */
public class ProfileAccessDeniedException extends RuntimeException {
    public ProfileAccessDeniedException(String message) {
        super(message);
    }
}
