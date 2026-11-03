package com.resolum.alivia.nursing.domain.exceptions;

/**
 * Thrown when the authenticated user has no active membership with the required access in the
 * household whose care network is requested.
 */
public class CareNetworkAccessDeniedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param householdId the requested household identifier
     */
    public CareNetworkAccessDeniedException(Long householdId) {
        super("You are not allowed to access the care network of household " + householdId + ".");
    }
}
