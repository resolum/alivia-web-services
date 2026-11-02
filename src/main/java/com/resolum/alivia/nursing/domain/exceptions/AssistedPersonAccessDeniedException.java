package com.resolum.alivia.nursing.domain.exceptions;

/**
 * Thrown when the authenticated user is neither an active relative nor an active caregiver
 * of the requested assisted person.
 */
public class AssistedPersonAccessDeniedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param assistedPersonProfileId the requested assisted person profile identifier
     */
    public AssistedPersonAccessDeniedException(Long assistedPersonProfileId) {
        super("You are not linked to assisted person " + assistedPersonProfileId + ".");
    }
}
