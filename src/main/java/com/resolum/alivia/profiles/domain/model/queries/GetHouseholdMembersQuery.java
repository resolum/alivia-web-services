package com.resolum.alivia.profiles.domain.model.queries;

/**
 * Query that asks for the members of a household on behalf of an authenticated user.
 *
 * @param householdId     the household identifier
 * @param requesterUserId the authenticated user that requests the members
 */
public record GetHouseholdMembersQuery(Long householdId, Long requesterUserId) {

    public GetHouseholdMembersQuery {
        if (householdId == null || householdId <= 0) {
            throw new IllegalArgumentException("Household id must be greater than 0");
        }
        if (requesterUserId == null || requesterUserId <= 0) {
            throw new IllegalArgumentException("Requester user id must be greater than 0");
        }
    }
}
