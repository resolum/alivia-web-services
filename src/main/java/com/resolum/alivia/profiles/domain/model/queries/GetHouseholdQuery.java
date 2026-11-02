package com.resolum.alivia.profiles.domain.model.queries;

/**
 * Query that asks for a household on behalf of an authenticated user.
 *
 * @param householdId     the household identifier
 * @param requesterUserId the authenticated user that requests the household
 */
public record GetHouseholdQuery(Long householdId, Long requesterUserId) {

    public GetHouseholdQuery {
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household id must be greater than 0");
        }
        if (requesterUserId == null || requesterUserId <= 0) {
            throw new IllegalArgumentException("Requester user id must be greater than 0");
        }
    }
}
