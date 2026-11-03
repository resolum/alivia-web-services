package com.resolum.alivia.nursing.domain.model.commands;

import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Objects;

/**
 * Command to link an existing user as caregiver of an assisted person of a household.
 *
 * @param requesterUserId         the authenticated user managing the household
 * @param householdId             the household of the assisted person
 * @param assistedPersonProfileId the assisted person profile
 * @param caregiverUserId         the user to link as caregiver
 */
public record LinkCaregiverCommand(UserId requesterUserId, Long householdId,
                                   PersonProfileId assistedPersonProfileId, UserId caregiverUserId) {

    /**
     * Validates the command.
     *
     * @throws IllegalArgumentException when the household is missing or not positive
     */
    public LinkCaregiverCommand {
        Objects.requireNonNull(requesterUserId, "Requester cannot be null.");
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household id must be greater than 0");
        }
        Objects.requireNonNull(assistedPersonProfileId, "Assisted person cannot be null.");
        Objects.requireNonNull(caregiverUserId, "Caregiver cannot be null.");
    }
}
