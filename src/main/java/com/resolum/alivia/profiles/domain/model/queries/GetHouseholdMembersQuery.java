package com.resolum.alivia.profiles.domain.model.queries;

/**
 * Query that asks for the members of a household.
 *
 * <p>The caller must authorize the request first through
 * {@link com.resolum.alivia.profiles.domain.services.HouseholdAccessService}.</p>
 *
 * @param householdId the household identifier
 */
public record GetHouseholdMembersQuery(Long householdId) {

    public GetHouseholdMembersQuery {
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household id must be greater than 0");
        }
    }
}
