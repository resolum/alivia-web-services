package com.resolum.alivia.profiles.domain.services;

import java.util.List;

import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdMembersQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdQuery;

/**
 * Domain query service for households and their members.
 *
 * <p>It does not authorize the caller. Every consumer must check the access first through
 * {@link HouseholdAccessService}.</p>
 */
public interface HouseholdQueryService {

    /**
     * Finds a household with its members.
     *
     * @param query the get household query
     * @return the household with its members
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException
     *         when the household does not exist
     */
    Household handle(GetHouseholdQuery query);

    /**
     * Finds the members of a household.
     *
     * @param query the get household members query
     * @return the household members
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException
     *         when the household does not exist
     */
    List<HouseholdMember> handle(GetHouseholdMembersQuery query);
}
