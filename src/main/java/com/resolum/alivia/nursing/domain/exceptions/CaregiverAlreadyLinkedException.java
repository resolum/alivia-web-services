package com.resolum.alivia.nursing.domain.exceptions;

import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Thrown when the caregiver already has a care membership with the assisted person. A revoked
 * membership counts too, because the pair is unique whatever its status.
 */
public class CaregiverAlreadyLinkedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param caregiverUserId         the caregiver user
     * @param assistedPersonProfileId the assisted person profile
     */
    public CaregiverAlreadyLinkedException(UserId caregiverUserId, PersonProfileId assistedPersonProfileId) {
        super("User " + caregiverUserId.userId() + " is already linked as caregiver of assisted person "
                + assistedPersonProfileId.personProfileId() + ".");
    }
}
