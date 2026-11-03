package com.resolum.alivia.profiles.domain.model.commands;

import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Command to add a user to a household as an active member.
 *
 * <p>The caller must authorize the request first through
 * {@link com.resolum.alivia.profiles.domain.services.HouseholdAccessService}.</p>
 *
 * @param householdId    the household identifier
 * @param userId         the user to add
 * @param responsibility the responsibility granted to the user
 */
public record AddHouseholdMemberCommand(Long householdId, UserId userId, HouseholdResponsibility responsibility) {

    /**
     * Validates the command.
     *
     * @throws IllegalArgumentException when the household is missing or not positive, or the user
     *                                  or the responsibility is missing
     */
    public AddHouseholdMemberCommand {
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household id must be greater than 0");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }
        if (responsibility == null) {
            throw new IllegalArgumentException("Responsibility cannot be null.");
        }
    }
}
