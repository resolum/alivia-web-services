package com.resolum.alivia.nursing.domain.exceptions;

import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;

/**
 * Thrown when the assisted person does not exist or does not belong to the household.
 */
public class AssistedPersonNotInHouseholdException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param householdId             the household identifier
     * @param assistedPersonProfileId the requested assisted person profile
     */
    public AssistedPersonNotInHouseholdException(Long householdId, PersonProfileId assistedPersonProfileId) {
        super("Assisted person " + assistedPersonProfileId.personProfileId()
                + " does not belong to household " + householdId + ".");
    }
}
