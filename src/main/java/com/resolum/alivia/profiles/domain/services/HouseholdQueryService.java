package com.resolum.alivia.profiles.domain.services;

import java.util.List;

import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.entities.HouseholdMember;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdMembersQuery;
import com.resolum.alivia.profiles.domain.model.queries.GetHouseholdQuery;

/**
 * Domain query service for households and their members.
 */
public interface HouseholdQueryService {

    /**
     * Finds a household when the requester is an active member.
     *
     * @param query the get household query
     * @return the household with its members
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException
     *         when the household does not exist
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdAccessDeniedException
     *         when the requester is not an active member
     */
    Household handle(GetHouseholdQuery query);

    /**
     * Finds the members of a household when the requester is an active member.
     *
     * @param query the get household members query
     * @return the household members
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdNotFoundException
     *         when the household does not exist
     * @throws com.resolum.alivia.profiles.domain.exceptions.HouseholdAccessDeniedException
     *         when the requester is not an active member
     */
    List<HouseholdMember> handle(GetHouseholdMembersQuery query);
}
