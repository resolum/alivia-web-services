package com.resolum.alivia.nursing.domain.model.queries;

import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

import java.util.Objects;

/**
 * Query for the caregivers of the assisted persons of a household.
 *
 * @param userId      the authenticated user
 * @param householdId the household identifier
 */
public record GetCaregiversQuery(UserId userId, Long householdId) {

    /**
     * Validates the query.
     *
     * @throws IllegalArgumentException when the household is missing or not positive
     */
    public GetCaregiversQuery {
        Objects.requireNonNull(userId, "User cannot be null.");
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household id must be greater than 0");
        }
    }
}
