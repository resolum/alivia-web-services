package com.resolum.alivia.profiles.domain.exceptions;

/**
 * Thrown when the authenticated user has no person or staff profile linked to its user id.
 */
public class OwnProfileNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param userId the authenticated user identifier
     */
    public OwnProfileNotFoundException(Long userId) {
        super("No profile is linked to user " + userId + ".");
    }
}
