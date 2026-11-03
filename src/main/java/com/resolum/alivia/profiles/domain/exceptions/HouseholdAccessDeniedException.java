package com.resolum.alivia.profiles.domain.exceptions;

/**
 * Thrown when a user tries to read a household without an active membership.
 *
 * <p>This is the minimal check required by TS-04 scenario 4 until AP-355 introduces
 * the full household authorization service.</p>
 */
public class HouseholdAccessDeniedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param householdId the household the user tried to read
     * @param userId      the authenticated user
     */
    public HouseholdAccessDeniedException(Long householdId, Long userId) {
        super("User " + userId + " is not an active member of household " + householdId + ".");
    }
}
