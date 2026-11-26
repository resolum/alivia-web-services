package com.resolum.alivia.tracking.domain.exceptions;

/** The user may read the assisted person, who has not been placed in a household yet, so there are no devices to show. */
public class AssistedPersonWithoutHousehold extends RuntimeException {
    public AssistedPersonWithoutHousehold(Long assistedPersonId) {
        super("The assisted person " + assistedPersonId + " has no household yet.");
    }
}
