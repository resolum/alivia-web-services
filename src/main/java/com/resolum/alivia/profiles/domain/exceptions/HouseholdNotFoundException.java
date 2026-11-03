package com.resolum.alivia.profiles.domain.exceptions;

/**
 * Thrown when a requested household does not exist.
 */
public class HouseholdNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param householdId the missing household identifier
     */
    public HouseholdNotFoundException(Long householdId) {
        super("Household with id " + householdId + " not found.");
    }
}
