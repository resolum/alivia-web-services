package com.resolum.alivia.nursing.domain.exceptions;

/**
 * Thrown when the household whose care network is requested does not exist.
 */
public class CareNetworkHouseholdNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param householdId the requested household identifier
     */
    public CareNetworkHouseholdNotFoundException(Long householdId) {
        super("Household " + householdId + " does not exist.");
    }
}
