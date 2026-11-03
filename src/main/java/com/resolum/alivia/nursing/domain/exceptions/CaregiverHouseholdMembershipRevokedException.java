package com.resolum.alivia.nursing.domain.exceptions;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Thrown when the user to link as caregiver has a revoked membership in the household. Linking
 * would leave a care membership for someone without access to the household, and Nursing never
 * reactivates a household membership on its own.
 */
public class CaregiverHouseholdMembershipRevokedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param caregiverUserId the caregiver user
     * @param householdId     the household identifier
     */
    public CaregiverHouseholdMembershipRevokedException(UserId caregiverUserId, Long householdId) {
        super("User " + caregiverUserId.userId() + " has a revoked membership in household " + householdId + ".");
    }
}
